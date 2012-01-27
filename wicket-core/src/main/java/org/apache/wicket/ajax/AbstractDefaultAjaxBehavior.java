/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wicket.ajax;

import org.apache.wicket.Application;
import org.apache.wicket.Component;
import org.apache.wicket.Page;
import org.apache.wicket.ajax.calldecorator.IAjaxCallDecoratorDelegate;
import org.apache.wicket.behavior.AbstractAjaxBehavior;
import org.apache.wicket.markup.html.IComponentAwareHeaderContributor;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.markup.html.WicketEventReference;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.resource.JavaScriptResourceReference;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.request.resource.ResourceReference;
import org.apache.wicket.settings.IDebugSettings;
import org.apache.wicket.util.lang.Args;
import org.apache.wicket.util.string.AppendingStringBuffer;
import org.apache.wicket.util.string.Strings;
import org.apache.wicket.util.time.Duration;

/**
 * The base class for Wicket's default AJAX implementation.
 * 
 * @since 1.2
 * 
 * @author Igor Vaynberg (ivaynberg)
 * 
 */
public abstract class AbstractDefaultAjaxBehavior extends AbstractAjaxBehavior
{
	private static final long serialVersionUID = 1L;

	/** reference to the default indicator gif file. */
	public static final ResourceReference INDICATOR = new PackageResourceReference(
		AbstractDefaultAjaxBehavior.class, "indicator.gif");

	/** reference to the default ajax debug support javascript file. */
	private static final ResourceReference JAVASCRIPT_DEBUG = new JavaScriptResourceReference(
		AbstractDefaultAjaxBehavior.class, "wicket-ajax-debug.js");

	/**
	 * Subclasses should call super.onBind()
	 * 
	 * @see org.apache.wicket.behavior.AbstractAjaxBehavior#onBind()
	 */
	@Override
	protected void onBind()
	{
		getComponent().setOutputMarkupId(true);
	}

	/**
	 * @see org.apache.wicket.behavior.AbstractAjaxBehavior#renderHead(Component,org.apache.wicket.markup.html.IHeaderResponse)
	 */
	@Override
	public void renderHead(Component component, IHeaderResponse response)
	{
		super.renderHead(component, response);

		response.renderJavaScriptReference(WicketEventReference.INSTANCE);
		response.renderJavaScriptReference(WicketAjaxReference.INSTANCE);

		final IDebugSettings debugSettings = Application.get().getDebugSettings();
		if (debugSettings.isAjaxDebugModeEnabled())
		{
			response.renderJavaScriptReference(JAVASCRIPT_DEBUG);
			response.renderJavaScript("wicketAjaxDebugEnable=true;", "wicket-ajax-debug-enable");
		}

		Url baseUrl = RequestCycle.get().getUrlRenderer().getBaseUrl();
		CharSequence ajaxBaseUrl = Strings.escapeMarkup(baseUrl.toString());
		response.renderJavaScript("Wicket.Ajax.baseUrl=\"" + ajaxBaseUrl + "\";",
			"wicket-ajax-base-url");

		contributeAjaxCallDecorator(component, response);
	}

	/**
	 * Contributes dependencies of IAjaxCallDecorator to the header
	 *
	 * @param component
	 *      the component this behavior is attached to
	 * @param response
	 *      the header response to write to
	 */
	private void contributeAjaxCallDecorator(Component component, IHeaderResponse response)
	{
		IAjaxCallDecorator ajaxCallDecorator = getAjaxCallDecorator();
		contributeComponentAwareHeaderContributor(ajaxCallDecorator, component, response);

		Object cursor = ajaxCallDecorator;
		while (cursor != null)
		{
			if (cursor instanceof IAjaxCallDecoratorDelegate)
			{
				cursor = ((IAjaxCallDecoratorDelegate) cursor).getDelegate();
				contributeComponentAwareHeaderContributor(cursor, component, response);
			}
			else
			{
				cursor = null;
			}
		}
	}

	/**
	 * Contributes to the header if {@literal target} is an instance of IComponentAwareHeaderContributor
	 *
	 * @param target
	 *      the candidate object that may contribute to the header
	 * @param component
	 *      the component this behavior is attached to
	 * @param response
	 *      the header response to write to
	 */
	private void contributeComponentAwareHeaderContributor(Object target, Component component, IHeaderResponse response)
	{
		if (target instanceof IComponentAwareHeaderContributor)
		{
			IComponentAwareHeaderContributor contributor = (IComponentAwareHeaderContributor)target;
			contributor.renderHead(component, response);
		}
	}

	/**
	 * @return ajax call decorator used to decorate the call generated by this behavior or null for
	 *         none
	 */
	protected IAjaxCallDecorator getAjaxCallDecorator()
	{
		return null;
	}

	/**
	 * @return javascript that will generate an ajax GET request to this behavior
	 */
	protected CharSequence getCallbackScript()
	{
		return generateCallbackScript("wicketAjaxGet('" + getCallbackUrl() + "'");
	}

	/**
	 * @return an optional javascript expression that determines whether the request will actually
	 *         execute (in form of return XXX;);
	 */
	protected CharSequence getPreconditionScript()
	{
		if (getComponent() instanceof Page)
		{
			return "return true;";
		}
		else
		{
			return "return Wicket.$('" + getComponent().getMarkupId() + "') != null;";
		}
	}

	/**
	 * @return javascript that will run when the ajax call finishes with an error status
	 */
	protected CharSequence getFailureScript()
	{
		return null;
	}

	/**
	 * @return javascript that will run when the ajax call finishes successfully
	 */
	protected CharSequence getSuccessScript()
	{
		return null;
	}

	/**
	 * Returns javascript that performs an ajax callback to this behavior. The script is decorated
	 * by the ajax callback decorator from
	 * {@link AbstractDefaultAjaxBehavior#getAjaxCallDecorator()}.
	 * 
	 * @param partialCall
	 *            JavaScript of a partial call to the function performing the actual ajax callback.
	 *            Must be in format <code>function(params,</code> with signature
	 *            <code>function(params, onSuccessHandler, onFailureHandler</code>. Example:
	 *            <code>wicketAjaxGet('callbackurl'</code>
	 * 
	 * @return script that performs ajax callback to this behavior
	 */
	protected CharSequence generateCallbackScript(final CharSequence partialCall)
	{
		final CharSequence onSuccessScript = getSuccessScript();
		final CharSequence onFailureScript = getFailureScript();
		final CharSequence precondition = getPreconditionScript();

		final IAjaxCallDecorator decorator = getAjaxCallDecorator();

		String indicatorId = findIndicatorId();

		CharSequence success = (onSuccessScript == null) ? "" : onSuccessScript;
		CharSequence failure = (onFailureScript == null) ? "" : onFailureScript;

		if (decorator != null)
		{
			success = decorator.decorateOnSuccessScript(getComponent(), success);
		}

		if (!Strings.isEmpty(indicatorId))
		{
			String hide = ";Wicket.hideIncrementally('" + indicatorId + "');";
			success = success + hide;
			failure = failure + hide;
		}

		if (decorator != null)
		{
			failure = decorator.decorateOnFailureScript(getComponent(), failure);
		}

		AppendingStringBuffer buff = new AppendingStringBuffer(256);
		buff.append("var ").append(IAjaxCallDecorator.WICKET_CALL_RESULT_VAR).append("=");
		buff.append(partialCall);

		buff.append(",function() { ").append(success).append("}.bind(this)");
		buff.append(",function() { ").append(failure).append("}.bind(this)");

		if (precondition != null)
		{
			buff.append(", function() {");
			if (Strings.isEmpty(indicatorId) == false)
			{
				// WICKET-4257 - ugly way to revert showIncrementally if
				// the precondition doesn't match after channel postpone
				buff.append("if (!function() {");
				buff.append(precondition);
				buff.append("}.bind(this)()) {Wicket.hideIncrementally('");
				buff.append(indicatorId);
				buff.append("');}");
			}
			buff.append(precondition);
			buff.append("}.bind(this)");
		}

		AjaxChannel channel = getChannel();
		if (channel != null)
		{
			if (precondition == null)
			{
				buff.append(", null");
			}
			buff.append(", '");
			buff.append(channel.getChannelName());
			buff.append("'");
		}

		buff.append(");");

		CharSequence call = buff;

		if (!Strings.isEmpty(indicatorId))
		{
			final AppendingStringBuffer indicatorWithPrecondition = new AppendingStringBuffer(
				"if (");
			if (precondition != null)
			{
				indicatorWithPrecondition.append("function(){")
					.append(precondition)
					.append("}.bind(this)()");
			}
			else
			{
				indicatorWithPrecondition.append("true");
			}
			indicatorWithPrecondition.append(") { Wicket.showIncrementally('")
				.append(indicatorId)
				.append("');}")
				.append(call);

			call = indicatorWithPrecondition;
		}

		if (decorator != null)
		{
			call = decorator.decorateScript(getComponent(), call);
		}

		return call;
	}

	/**
	 * @return the name and the type of the channel to use when processing Ajax calls at the client
	 *         side
	 * @deprecated Use {@link #getChannel()} instead
	 */
	// TODO Wicket.next - Remove this method
	@Deprecated
	protected String getChannelName()
	{
		AjaxChannel channel = getChannel();
		return channel != null ? channel.getChannelName() : null;
	}

	/**
	 * Provides an AjaxChannel for this Behavior.
	 * 
	 * @return an AjaxChannel - Defaults to null.
	 * */
	protected AjaxChannel getChannel()
	{
		return null;
	}

	/**
	 * Finds the markup id of the indicator. The default search order is: component, behavior,
	 * component's parent hierarchy.
	 * 
	 * @return markup id or <code>null</code> if no indicator found
	 */
	protected String findIndicatorId()
	{
		if (getComponent() instanceof IAjaxIndicatorAware)
		{
			return ((IAjaxIndicatorAware)getComponent()).getAjaxIndicatorMarkupId();
		}

		if (this instanceof IAjaxIndicatorAware)
		{
			return ((IAjaxIndicatorAware)this).getAjaxIndicatorMarkupId();
		}

		Component parent = getComponent().getParent();
		while (parent != null)
		{
			if (parent instanceof IAjaxIndicatorAware)
			{
				return ((IAjaxIndicatorAware)parent).getAjaxIndicatorMarkupId();
			}
			parent = parent.getParent();
		}
		return null;
	}

	/**
	 * @see org.apache.wicket.behavior.IBehaviorListener#onRequest()
	 */
	public final void onRequest()
	{
		WebApplication app = (WebApplication)getComponent().getApplication();
		AjaxRequestTarget target = app.newAjaxRequestTarget(getComponent().getPage());

		RequestCycle requestCycle = RequestCycle.get();
		requestCycle.scheduleRequestHandlerAfterCurrent(target);

		respond(target);
	}

	/**
	 * @param target
	 *            The AJAX target
	 */
	// TODO rename this to onEvent or something? respond is mostly the same as
	// onRender
	// this is not the case this is still the event handling period. respond is
	// called
	// in the RequestCycle on the AjaxRequestTarget..
	protected abstract void respond(AjaxRequestTarget target);

	/**
	 * Wraps the provided javascript with a throttled block. Throttled behaviors only execute once
	 * within the given delay even though they are triggered multiple times.
	 * <p>
	 * For example, this is useful when attaching an event behavior to the onkeypress event. It is
	 * not desirable to have an ajax call made every time the user types so we throttle that call to
	 * a desirable delay, such as once per second. This gives us a near real time ability to provide
	 * feedback without overloading the server with ajax calls.
	 * 
	 * @param script
	 *            javascript to be throttled
	 * @param throttleId
	 *            the id of the throttle to be used. Usually this should remain constant for the
	 *            same javascript block.
	 * @param throttleDelay
	 *            time span within which the javascript block will only execute once
	 * @return wrapped javascript
	 */
	public static CharSequence throttleScript(CharSequence script, String throttleId,
		Duration throttleDelay)
	{
		Args.notEmpty(script, "script");
		Args.notEmpty(throttleId, "throttleId");
		Args.notNull(throttleDelay, "throttleDelay");

		return new AppendingStringBuffer("wicketThrottler.throttle( '").append(throttleId)
			.append("', ")
			.append(throttleDelay.getMilliseconds())
			.append(", function() { ")
			.append(script)
			.append("}.bind(this));");
	}
}

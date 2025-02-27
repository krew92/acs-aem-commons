/*
 * #%L
 * ACS AEM Commons Bundle
 * %%
 * Copyright (C) 2017 Adobe
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.adobe.acs.commons.wcm.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.adobe.acs.commons.util.BufferedHttpServletResponse;
import com.adobe.acs.commons.util.BufferedServletOutput.ResponseWriteMethod;
import com.day.cq.commons.PathInfo;
import com.day.cq.wcm.api.WCMMode;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang.text.StrLookup;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.*;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.engine.EngineConstants;
import org.apache.sling.xss.XSSAPI;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * The Environment filter consists of 2 filters:
 * * the environment filter, which is registered directly to the HTTP whiteboard, and which can cover
 *   also non-Sling applications (like CRXDE and the OSGI webconsole)
 * * a Sling filter, which is required for the filtering based on the WCM modes.
 * 
 * The environment indicator output is written by the "outer" filter, but its decision might be overwritten
 * by the Sling Filter. The status is stored as a request attribute.
 * 
 *
 */


@Component(
        label = "ACS AEM Commons - AEM Environment Indicator",
        description = "Adds a visual cue to the AEM WebUI indicating which environment is being access "
                + "(localdev, dev, qa, staging)",
        metatype = true,
        policy = ConfigurationPolicy.REQUIRE
)
public class AemEnvironmentIndicatorFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(AemEnvironmentIndicatorFilter.class);

    private static final String DIV_ID = "acs-commons-env-indicator";
    
    static final String INJECT_INDICATOR_PARAMETER = "AemEnvironmentIndicatorFilter.includeIndicator";
    static final String NO_EXTENSION_PLACEHOLDER = "<NONE>";

    private static final String BASE_DEFAULT_STYLE =
            ";background-image:url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAGXRFWHRTb2Z0d2FyZQBBZG9iZSBJbWFnZVJlYWR5ccllPAAAA3NpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADw/eHBhY2tldCBiZWdpbj0i77u/IiBpZD0iVzVNME1wQ2VoaUh6cmVTek5UY3prYzlkIj8+IDx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IkFkb2JlIFhNUCBDb3JlIDUuNS1jMDIxIDc5LjE1NDkxMSwgMjAxMy8xMC8yOS0xMTo0NzoxNiAgICAgICAgIj4gPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4gPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIgeG1sbnM6eG1wTU09Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9tbS8iIHhtbG5zOnN0UmVmPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvc1R5cGUvUmVzb3VyY2VSZWYjIiB4bWxuczp4bXA9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC8iIHhtcE1NOk9yaWdpbmFsRG9jdW1lbnRJRD0ieG1wLmRpZDo5ZmViMDk1Ni00MTMwLTQ0NGMtYWM3Ny02MjU0NjY0OTczZWIiIHhtcE1NOkRvY3VtZW50SUQ9InhtcC5kaWQ6MDk4RTBGQkYzMjA5MTFFNDg5MDFGQzVCQkEyMjY0NDQiIHhtcE1NOkluc3RhbmNlSUQ9InhtcC5paWQ6MDk4RTBGQkUzMjA5MTFFNDg5MDFGQzVCQkEyMjY0NDQiIHhtcDpDcmVhdG9yVG9vbD0iQWRvYmUgUGhvdG9zaG9wIENDIChNYWNpbnRvc2gpIj4gPHhtcE1NOkRlcml2ZWRGcm9tIHN0UmVmOmluc3RhbmNlSUQ9InhtcC5paWQ6Mjc5NmRkZmItZDVlYi00N2RlLWI1NDMtNDgxNzU2ZjIwZDc1IiBzdFJlZjpkb2N1bWVudElEPSJ4bXAuZGlkOjlmZWIwOTU2LTQxMzAtNDQ0Yy1hYzc3LTYyNTQ2NjQ5NzNlYiIvPiA8L3JkZjpEZXNjcmlwdGlvbj4gPC9yZGY6UkRGPiA8L3g6eG1wbWV0YT4gPD94cGFja2V0IGVuZD0iciI/Ps64/vsAAAAkSURBVHjaYvz//z8DGjBmAAkiYWOwInQBZEFjZB0YAiAMEGAAVBk/wkPTSYQAAAAASUVORK5CYII=');"
                    + "border-bottom: 1px solid rgba(0, 0, 0, .25);"
                    + "box-sizing: border-box;"
                    + "-moz-box-sizing: border-box;"
                    + "-webkit-box-sizing: border-box;"
                    + "position: fixed;"
                    + "left: 0;"
                    + "top: 0;"
                    + "right: 0;"
            + "height: 5px;"
            + "z-index: 100000000000000;";

    private static final String TITLE_UPDATE_SCRIPT = "<script>(function() { var c = 0; t = '%s' + ' | ' + document.title, "
            + "i = setInterval(function() { if (document.title === t && c++ > 10) { clearInterval(i); } else { document.title = t; } }, 1500); "
            + "document.title = t; })();</script>\n";

    @Reference
    private XSSAPI xss;

    /* Property: Default Color */

    private String color = "";

    @Property(label = "Color",
            description = "The color of the indicator bar; takes any valid value"
                    + " for CSS's 'background-color' attribute."
                    + " This is only effective if no 'CSS Override' is provided or 'Always Include Color CSS' is set to true.",
            value = "")
    public static final String PROP_COLOR = "css-color";

    /* Property: CSS Override */

    private String cssOverride = "";

    @Property(label = "CSS Override",
            description = "Accepts any valid CSS to style the AEM indicator div. All CSS rules must only be "
                    + "scoped to #" + DIV_ID + " { .. }",
            value = "")
    public static final String PROP_CSS_OVERRIDE = "css-override";

    /* Property: Inner HTML */

    private String innerHTML = "";

    @Property(label = "Inner HTML",
            description = "Any additional HTML required; Will be injected into a div with"
                    + " id='" + DIV_ID + "'",
            value = "")
    public static final String PROP_INNER_HTML = "inner-html";


    /* Property: Browser Title Prefix */

    private static final String DEFAULT_TITLE_PREFIX = "";

    private String titlePrefix = DEFAULT_TITLE_PREFIX;

    /* Property: Always Include Base CSS */
    
    private boolean alwaysIncludeBaseCss;
    
    @Property(label = "Always Include Base CSS",
        description = "Always include the base CSS scoped to #" + DIV_ID + " { .. }",
        boolValue = false)
    public static final String PROP_ALWAYS_INCLUDE_BASE_CSS = "always-include-base-css";

    /* Property: Always Include Color CSS */
    
    private boolean alwaysIncludeColorCss;
    
    @Property(label = "Always Include Color CSS",
        description = "Always include the color CSS scoped to #" + DIV_ID + " { .. }",
        boolValue = false)
    public static final String PROP_ALWAYS_INCLUDE_COLOR_CSS = "always-include-color-css";

    @Property(label = "Browser Title",
            description = "A prefix to add to the browser tab/window title; <THIS VALUE> | <ORIGINAL DOC TITLE>",
            value = DEFAULT_TITLE_PREFIX)
    public static final String PROP_TITLE_PREFIX = "browser-title-prefix";

    private static final String[] DEFAULT_EXCLUDED_WCMMODES = {"DISABLED"};
    @Property (label = "Excluded WCM modes",
            description = "Do not display the indicator when these WCM modes",
            cardinality = Integer.MAX_VALUE)
    public static final String PROP_EXCLUDED_WCMMODES = "excluded-wcm-modes";
    private String[] excludedWCMModes;


    private static final String[] DEFAULT_ALLOWED_EXTENSIONS = {"html", "htm", "jsp", NO_EXTENSION_PLACEHOLDER};
    @Property (label = "Allowed URI extensions",
            description = "Only inject the environment indicator on URI that use these extensions. Use '"+ NO_EXTENSION_PLACEHOLDER + "' to match on no extension.",
            cardinality = Integer.MAX_VALUE)
    public static final String PROP_ALLOWED_EXTENSIONS = "allowed-extensions";
    private String[] allowedExtensions;

    private String css = "";

    private ServiceRegistration filterRegistration;
    
    private ServiceRegistration innerFilterRegistration;

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        // no-op
    }

    @Override
    @SuppressWarnings("squid:S3776")
    public final void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse,
                               final FilterChain filterChain) throws IOException, ServletException {

        if (!(servletRequest instanceof HttpServletRequest)
                || !(servletResponse instanceof HttpServletResponse)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        final HttpServletRequest request = (HttpServletRequest) servletRequest;
        final HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (!this.accepts(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try (BufferedHttpServletResponse capturedResponse =
                new BufferedHttpServletResponse(response, new StringWriter(), null)) {

            request.setAttribute(INJECT_INDICATOR_PARAMETER, Boolean.TRUE);

            log.debug("Executing the rest of the filter chain");
            filterChain.doFilter(request, capturedResponse);
            log.debug("Executing the rest of the filter chain");

            if (StringUtils.contains(response.getContentType(), "html") && innerFilterAcceptsInjection(request)) {
                // Get contents
                final String contents = capturedResponse.getBufferedServletOutput()
                        .getWriteMethod() == ResponseWriteMethod.WRITER
                                ? capturedResponse.getBufferedServletOutput().getBufferedString()
                                : null;

                if (contents != null) {
                    final int bodyIndex = contents.indexOf("</body>");

                    if (bodyIndex != -1) {
                        // prevent the captured response from being given out a 2nd time via the implicit close()
                        capturedResponse.setFlushBufferOnClose(false);
                        final PrintWriter printWriter = response.getWriter();
                        printWriter.write(contents.substring(0, bodyIndex));

                        writeEnvironmentIndicator(css, innerHTML, titlePrefix, printWriter);

                        printWriter.write(contents.substring(bodyIndex));
                    }
                }
            }
        }
    }
    
    boolean innerFilterAcceptsInjection(HttpServletRequest request) {
        return request.getAttribute(INJECT_INDICATOR_PARAMETER).equals(Boolean.TRUE);
    }

    void writeEnvironmentIndicator(String css, String innerHTML, String titlePrefix,
            PrintWriter printWriter) {
        if (StringUtils.isNotBlank(css)) {
            printWriter.write("<style>" + css + " </style>");
            printWriter.write("<div id=\"" + DIV_ID + "\">" + innerHTML + "</div>");
        }

        if (StringUtils.isNotBlank(titlePrefix)) {
            printWriter.printf(TITLE_UPDATE_SCRIPT, titlePrefix);
        }
    }

    @Override
    public void destroy() {
        // no-op
    }

    @SuppressWarnings("squid:S3923")
    boolean accepts(final HttpServletRequest request) {
        if (isImproperlyConfigured(css, titlePrefix)) {
            // Only accept is properly configured
            log.warn(
                    "AEM Environment Indicator is not properly configured; If this feature is unwanted, "
                            + "remove the OSGi configuration and disable completely.");
            return false;
        } else if (isUnsupportedExtension(request.getRequestURI())) {
            log.debug("Request's extension does not match allowed extensions");
            return false;
        } else if (isUnsupportedRequestMethod(request.getMethod())) {
            log.debug("Request was not a GET request");
            return false;
        } else if (isXhr(request.getHeader("X-Requested-With"))) {
            log.debug("Request was an XHR");
            return false;
        } else if (hasAemEditorReferrer(request.getHeader("Referer"), request.getRequestURI())) {
            log.debug("Request was for a page in an editor");
            return false;
        }
        // Checking for WcmMode does not make sense, it is not available here
        log.debug("All checks pass, filter can execute");
        return true;
    }

    protected boolean isUnsupportedExtension(String requestURI) {
        if (ArrayUtils.isEmpty(allowedExtensions)) {
            return false;
        }

        final PathInfo pathInfo = new PathInfo(requestURI);
        final String extension = pathInfo.getExtension();

        if (StringUtils.isBlank(extension)) {
            // Special case handle of blank extension
            return !ArrayUtils.contains(allowedExtensions, NO_EXTENSION_PLACEHOLDER);
        } else {
            // If extension is not blank, check to make sure it is allowed
            return !ArrayUtils.contains(allowedExtensions, extension);
        }
    }

    boolean isImproperlyConfigured(final String css, final String titlePrefix) {
        return StringUtils.isBlank(css) && StringUtils.isBlank(titlePrefix);
    }

    boolean isUnsupportedRequestMethod(final String requestMethod) {
        return !StringUtils.equalsIgnoreCase("get", requestMethod);
    }

    boolean isXhr(final String headerValue) {
        return StringUtils.equals(headerValue, "XMLHttpRequest");
    }

    boolean hasAemEditorReferrer(final String headerValue, final String requestUri) {
        return StringUtils.endsWith(headerValue, "/editor.html" + requestUri)
                || StringUtils.endsWith(headerValue, "/cf");
    }
    
    @Activate
    @SuppressWarnings("squid:S1149")
    protected final void activate(ComponentContext ctx) {
        Dictionary<?, ?> config = ctx.getProperties();

        color = PropertiesUtil.toString(config.get(PROP_COLOR), "");
        cssOverride = PropertiesUtil.toString(config.get(PROP_CSS_OVERRIDE), "");
        innerHTML = PropertiesUtil.toString(config.get(PROP_INNER_HTML), "");
        innerHTML = new StrSubstitutor(StrLookup.systemPropertiesLookup()).replace(innerHTML);
        alwaysIncludeBaseCss = PropertiesUtil.toBoolean(PROP_ALWAYS_INCLUDE_BASE_CSS, false);
        alwaysIncludeColorCss = PropertiesUtil.toBoolean(PROP_ALWAYS_INCLUDE_COLOR_CSS, false);

        alwaysIncludeBaseCss = PropertiesUtil.toBoolean(PROP_ALWAYS_INCLUDE_BASE_CSS, false);
        alwaysIncludeColorCss = PropertiesUtil.toBoolean(PROP_ALWAYS_INCLUDE_COLOR_CSS, false);

        StringBuilder cssSb = new StringBuilder();

        if (shouldUseBaseCss(alwaysIncludeBaseCss, cssOverride, color)) {
            cssSb.append(createBaseCss());
        }

        if (shouldUseColorCss(alwaysIncludeColorCss, cssOverride, color)) {
            cssSb.append(createColorCss(color));
        }

        if (StringUtils.isNotBlank(cssOverride)) {
            cssSb.append(cssOverride);
        }

        css = cssSb.toString();

        titlePrefix = xss.encodeForJSString(
                PropertiesUtil.toString(config.get(PROP_TITLE_PREFIX), "").toString());
        
        excludedWCMModes = PropertiesUtil.toStringArray(config.get(PROP_EXCLUDED_WCMMODES),
                DEFAULT_EXCLUDED_WCMMODES);

        allowedExtensions = PropertiesUtil.toStringArray(config.get(PROP_ALLOWED_EXTENSIONS),
                DEFAULT_ALLOWED_EXTENSIONS);

        if (StringUtils.isNotBlank(css) || StringUtils.isNotBlank(titlePrefix)) {
            Dictionary<String, String> filterProps = new Hashtable<String, String>();
            filterProps.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_PATTERN, "/");
            filterProps.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT,
                    "(" + HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME + "=*)");
            filterRegistration = ctx.getBundleContext().registerService(Filter.class.getName(),
                    this, filterProps);
            
            // Register the innerFilter so it is invoked after the WcmRequestFilter (Ranking = 2000)
            Dictionary<String, Object> innerFilterProps = new Hashtable<>();
            innerFilterProps.put(EngineConstants.SLING_FILTER_SCOPE,EngineConstants.FILTER_SCOPE_REQUEST);
            innerFilterProps.put(Constants.SERVICE_RANKING, 1000);
            Filter innerFilter = new InnerEnvironmentIndicatorFilter(excludedWCMModes);
            innerFilterRegistration = ctx.getBundleContext().registerService(Filter.class.getName(), innerFilter, innerFilterProps);
        }
    }

    String createBaseCss() {
        return "#" + DIV_ID + " { "
                + BASE_DEFAULT_STYLE
                + " }";
    }

    String createColorCss(final String providedColor) {
        return "#" + DIV_ID + " { "
                + "background-color:" + providedColor
                + "; }";
    }

    boolean shouldUseBaseCss(boolean alwaysInclude, String cssOverride, String color) {
        return alwaysInclude
                || StringUtils.isBlank(cssOverride) && StringUtils.isNotBlank(color);
    }

    boolean shouldUseColorCss(boolean alwaysInclude, String cssOverride, String color) {
        return alwaysInclude
                || StringUtils.isBlank(cssOverride) && StringUtils.isNotBlank(color);
    }

    @Deactivate
    protected final void deactivate(final Map<String, String> config) {
        if (filterRegistration != null) {
            filterRegistration.unregister();
            filterRegistration = null;
        }
        if (innerFilterRegistration != null) {
            innerFilterRegistration.unregister();
            innerFilterRegistration = null;
        }

        // Reset CSS variable
        css = "";
    }

    WCMMode getWcmMode(HttpServletRequest request) {
        return WCMMode.fromRequest(request);
    }

    /*
     * Used for testing
     */
    String getCss() {
        return css;
    }

    String getTitlePrefix() {
        return titlePrefix;
    }
    
    protected static class InnerEnvironmentIndicatorFilter implements Filter {

        String[] excludedWcmModes;

        public InnerEnvironmentIndicatorFilter(String[] excludedWcmModes) {
            this.excludedWcmModes = excludedWcmModes;
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
            // ignore
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {

            SlingHttpServletRequest req = (SlingHttpServletRequest) request;

            WCMMode mode = WCMMode.fromRequest(request);
            if (isDisallowedWcmMode(mode, excludedWcmModes)) {
                request.setAttribute(INJECT_INDICATOR_PARAMETER, Boolean.FALSE);
                String msg = String.format(
                        "reject inclusion of environment indicator, found wcmmode '%s' in exclusion list %s",
                        mode.name(), ArrayUtils.toString(excludedWcmModes));
                req.getRequestProgressTracker().log(msg);
            }
            chain.doFilter(request, response);
        }

        boolean isDisallowedWcmMode(WCMMode currentMode, String[] excludedWcmModes) {
            return currentMode == null || StringUtils.equalsAnyIgnoreCase(currentMode.name(), excludedWcmModes);
        }

        @Override
        public void destroy() {
            // ignore
        }

    }
    
}

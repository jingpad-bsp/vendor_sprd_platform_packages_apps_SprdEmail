/*
 * Copyright (C) 2014 The Android Open Source Project
 *
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
 */
package com.android.mail.utils;

import android.os.Looper;
import android.util.Log;

import com.android.mail.perf.Timer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.owasp.html.AttributePolicy;
import org.owasp.html.CssSchema;
import org.owasp.html.ElementPolicy;
import org.owasp.html.FilterUrlByProtocolAttributePolicy;
import org.owasp.html.Handler;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.HtmlStreamRenderer;
import org.owasp.html.PolicyFactory;

import java.util.List;

/**
 * This sanitizer is meant to strip all scripts and any malicious HTML from untrusted emails. It
 * uses the <a href="https://www.owasp.org/index.php/OWASP_Java_HTML_Sanitizer_Project">OWASP Java
 * HTML Sanitizer Project</a> to whitelist the subset of HTML elements and attributes as well as CSS
 * properties that are considered safe. Any unmatched HTML or CSS is discarded.
 *
 * All URLS are scrubbed to ensure they match the blessed form of "http://the.url.here",
 * "https://the.url.here" or "mailto:address@server.com" and cannot resemble "javascript:badness()"
 * or comparable.
 */
public final class HtmlSanitizer {

    /**
     * This version number should be bumped each time a meaningful change is made to this sanitizer
     * configuration which influences its output. It is compared against a minimum target version
     * number. If it meets or exceeds the minimum target version, the result of the sanitizer is
     * free to be shown in a standard webview. If it does not meet the minimum target version then
     * the sanitized output is deemed untrustworthy and is shown in a sandboxed webview with
     * javascript execution disabled.
     */
    public static final int VERSION = 1;

    private static final String LOG_TAG = LogTag.getLogTag();

    /**
     * The following CSS properties do not appear in the default whitelist from OWASP, but they
     * improve the fidelity of the HTML display without unacceptable risk.
     */
    private static final CssSchema ADDITIONAL_CSS = CssSchema.withProperties(ImmutableSet.of(
            "float",
            "display"
    ));

    /**
     * Translates the body tag into the div tag
     */
    private static final ElementPolicy TRANSLATE_BODY_TO_DIV = new ElementPolicy() {
        public String apply(String elementName, List<String> attrs) {
            return "div";
        }
    };

    /**
     * Translates <div> tags surrounding quoted text into <div class="elided-text"> which allows
     * quoted text collapsing in ConversationViewFragment.
     */
    private static final ElementPolicy TRANSLATE_DIV_CLASS = new ElementPolicy() {
        public String apply(String elementName, List<String> attrs) {
            boolean showHideQuotedText = false;
            /* SPRD: Modify for bug 541340 {@ */
            boolean showRefHideQuotedText = false;
            /* @} */
            // check if the class attribute is listed
            final int classIndex = attrs.indexOf("class");
            if (classIndex >= 0) {
                // remove the class attribute and its value
                final String value = attrs.remove(classIndex + 1);
                attrs.remove(classIndex);

                // gmail and yahoo use a specific div class name to indicate quoted text
                showHideQuotedText = "gmail_quote".equals(value) || "yahoo_quoted".equals(value);
                /* SPRD: Modify for bug 541340 {@ */
                showRefHideQuotedText = "quote".equals(value);
                /* @} */
            }

            // check if the id attribute is listed
            final int idIndex = attrs.indexOf("id");
            if (idIndex >= 0) {
                // remove the id attribute and its value
                final String value = attrs.remove(idIndex + 1);
                attrs.remove(idIndex);

                // AOL uses a specific id value to indicate quoted text
                showHideQuotedText = value.startsWith("AOLMsgPart");
            }

            // insert a class attribute with a value of "elided-text" to hide/show quoted text
            if (showHideQuotedText) {
                attrs.add("class");
                attrs.add("elided-text");
            }
            /* SPRD: Modify for bug 541340 {@ */
            if (showRefHideQuotedText) {
                attrs.add("class");
                attrs.add("quote");
            }
            /* @} */
            return "div";
        }
    };

    /**
     * Disallow "cid:" and "mailto:" urls on all tags not &lt;a&gt; or &lt;img&gt;.
     */
    private static final AttributePolicy URL_PROTOCOLS =
            new FilterUrlByProtocolAttributePolicy(ImmutableList.of("http", "https"));

    /**
     * Disallow the "cid:" url on links. Do allow "mailto:" urls to support sending mail.
     */
    /* SPRD:bug520737 modify begin @{ */
    /* SPRD:bug615256 add rtsp deal @{ */
    private static final AttributePolicy A_HREF_PROTOCOLS =
            new FilterUrlByProtocolAttributePolicy(ImmutableList.of("mailto", "http", "https", "tel", "rtsp"));
    /* @} */
    /* @} */
    /**
     * Disallow the "mailto:" url on images so that "Show pictures" can't be used to start composing
     * a bajillion emails. Do allow "cid:" urls to support inline image attachments.
     */
    private static final AttributePolicy IMG_SRC_PROTOCOLS =
            new FilterUrlByProtocolAttributePolicy(ImmutableList.of("cid", "http", "https"));

    /**
     * This sanitizer policy removes these elements and the content within:
     * <ul>
     *     <li>APPLET</li>
     *     <li>FRAMESET</li>
     *     <li>OBJECT</li>
     *     <li>SCRIPT</li>
     *     <li>STYLE</li>
     *     <li>TITLE</li>
     * </ul>
     *
     * This sanitizer policy removes these elements but preserves the content within:
     * <ul>
     *     <li>BASEFONT</li>
     *     <li>FRAME</li>
     *     <li>HEAD</li>
     *     <li>IFRAME</li>
     *     <li>ISINDEX</li>
     *     <li>LINK</li>
     *     <li>META</li>
     *     <li>NOFRAMES</li>
     *     <li>PARAM</li>
     *     <li>NOSCRIPT</li>
     * </ul>
     *
     * This sanitizer policy removes these attributes from all elements:
     * <ul>
     *     <li>code</li>
     *     <li>codebase</li>
     *     <li>id</li>
     *     <li>for</li>
     *     <li>headers</li>
     *     <li>onblur</li>
     *     <li>onchange</li>
     *     <li>onclick</li>
     *     <li>ondblclick</li>
     *     <li>onfocus</li>
     *     <li>onkeydown</li>
     *     <li>onkeypress</li>
     *     <li>onkeyup</li>
     *     <li>onload</li>
     *     <li>onmousedown</li>
     *     <li>onmousemove</li>
     *     <li>onmouseout</li>
     *     <li>onmouseover</li>
     *     <li>onmouseup</li>
     *     <li>onreset</li>
     *     <li>onselect</li>
     *     <li>onsubmit</li>
     *     <li>onunload</li>
     *     <li>tabindex</li>
     * </ul>
     */
    /* SPRD:bug520737 modify begin @{ */
    /* SPRD:bug615256 add rtsp deal @{ */
    private static final PolicyFactory POLICY_DEFINITION = new HtmlPolicyBuilder()
            .allowAttributes("dir").matching(true, "ltr", "rtl").globally()
            .allowUrlProtocols("cid", "http", "https", "mailto", "tel", "rtsp")
            .allowStyling(CssSchema.union(CssSchema.DEFAULT, ADDITIONAL_CSS))
            .disallowTextIn("applet", "frameset", "object", "script", "style", "title")
            .allowElements("a")
                .allowAttributes("coords", "name", "shape").onElements("a")
                .allowAttributes("href").matching(A_HREF_PROTOCOLS).onElements("a")
            .allowElements("abbr").allowAttributes("title").onElements("abbr")
            .allowElements("acronym").allowAttributes("title").onElements("acronym")
            .allowElements("address")
            .allowElements("area")
                .allowAttributes("alt", "coords", "nohref", "name", "shape").onElements("area")
                .allowAttributes("href").matching(URL_PROTOCOLS).onElements("area")
            .allowElements("article")
            .allowElements("aside")
            .allowElements("b")
            .allowElements("base")
                .allowAttributes("href").matching(URL_PROTOCOLS).onElements("base")
            .allowElements("bdi").allowAttributes("dir").onElements("bdi")
            .allowElements("bdo").allowAttributes("dir").onElements("bdo")
            .allowElements("big")
            .allowElements("blockquote").allowAttributes("cite").onElements("blockquote")
            .allowElements(TRANSLATE_BODY_TO_DIV, "body")
            .allowElements("br").allowAttributes("clear").onElements("br")
            .allowElements("button")
                .allowAttributes("autofocus", "disabled", "form", "formaction", "formenctype",
                        "formmethod", "formnovalidate", "formtarget", "name", "type", "value")
            .onElements("button")
            .allowElements("canvas").allowAttributes("width", "height").onElements("canvas")
            .allowElements("caption").allowAttributes("align").onElements("caption")
            .allowElements("center")
            .allowElements("cite")
            .allowElements("code")
            .allowElements("col")
                .allowAttributes("align", "bgcolor", "char", "charoff", "span", "valign", "width")
            .onElements("col")
            .allowElements("colgroup")
                .allowAttributes("align", "char", "charoff", "span", "valign", "width")
            .onElements("colgroup")
            .allowElements("datalist")
            .allowElements("dd")
            .allowElements("del").allowAttributes("cite", "datetime").onElements("del")
            .allowElements("details")
            .allowElements("dfn")
            .allowElements("dir").allowAttributes("compact").onElements("dir")
            .allowElements(TRANSLATE_DIV_CLASS, "div")
                .allowAttributes("align", "background", "class", "id")
            .onElements("div")
            .allowElements("dl")
            .allowElements("dt")
            .allowElements("em")
            .allowElements("fieldset")
                .allowAttributes("disabled", "form", "name")
            .onElements("fieldset")
            .allowElements("figcaption")
            .allowElements("figure")
            .allowElements("font").allowAttributes("color", "face", "size").onElements("font")
            .allowElements("footer")
            .allowElements("form")
                .allowAttributes("accept", "action", "accept-charset", "autocomplete", "enctype",
                        "method", "name", "novalidate", "target")
            .onElements("form")
            .allowElements("header")
            .allowElements("h1").allowAttributes("align").onElements("h1")
            .allowElements("h2").allowAttributes("align").onElements("h2")
            .allowElements("h3").allowAttributes("align").onElements("h3")
            .allowElements("h4").allowAttributes("align").onElements("h4")
            .allowElements("h5").allowAttributes("align").onElements("h5")
            .allowElements("h6").allowAttributes("align").onElements("h6")
            .allowElements("hr")
                .allowAttributes("align", "noshade", "size", "width")
            .onElements("hr")
            .allowElements("i")
            .allowElements("img")
                .allowAttributes("src").matching(IMG_SRC_PROTOCOLS).onElements("img")
                .allowAttributes("longdesc").matching(URL_PROTOCOLS).onElements("img")
                .allowAttributes("align", "alt", "border", "crossorigin", "height", "hspace",
                        "ismap", "usemap", "vspace", "width")
            .onElements("img")
            .allowElements("input")
                .allowAttributes("src").matching(URL_PROTOCOLS).onElements("input")
                .allowAttributes("formaction").matching(URL_PROTOCOLS).onElements("input")
                .allowAttributes("accept", "align", "alt", "autocomplete", "autofocus", "checked",
                        "disabled", "form", "formenctype", "formmethod", "formnovalidate",
                        "formtarget", "height", "list", "max", "maxlength", "min", "multiple",
                        "name", "pattern", "placeholder", "readonly", "required", "size", "step",
                        "type", "value", "width")
            .onElements("input")
            .allowElements("ins")
                .allowAttributes("cite").matching(URL_PROTOCOLS).onElements("ins")
                .allowAttributes("datetime").onElements("ins")
            .allowElements("kbd")
            .allowElements("keygen")
                .allowAttributes("autofocus", "challenge", "disabled", "form", "keytype", "name")
            .onElements("keygen")
            .allowElements("label").allowAttributes("form").onElements("label")
            .allowElements("legend").allowAttributes("align").onElements("legend")
            .allowElements("li").allowAttributes("type", "value").onElements("li")
            .allowElements("main")
            .allowElements("map").allowAttributes("name").onElements("map")
            .allowElements("mark")
            .allowElements("menu").allowAttributes("label", "type").onElements("menu")
            .allowElements("menuitem")
                .allowAttributes("icon").matching(URL_PROTOCOLS).onElements("menuitem")
                .allowAttributes("checked", "command", "default", "disabled", "label", "type",
                        "radiogroup").onElements("menuitem")
            .allowElements("meter")
                .allowAttributes("form", "high", "low", "max", "min", "optimum", "value")
            .onElements("meter")
            .allowElements("nav")
            .allowElements("ol")
                .allowAttributes("compact", "reversed", "start", "type")
            .onElements("ol")
            .allowElements("optgroup").allowAttributes("disabled", "label").onElements("optgroup")
            .allowElements("option")
                .allowAttributes("disabled", "label", "selected", "value")
            .onElements("option")
            .allowElements("output").allowAttributes("form", "name").onElements("output")
            .allowElements("p").allowAttributes("align").onElements("p")
            .allowElements("pre").allowAttributes("width").onElements("pre")
            .allowElements("progress").allowAttributes("max", "value").onElements("progress")
            .allowElements("q").allowAttributes("cite").matching(URL_PROTOCOLS).onElements("q")
            .allowElements("rp")
            .allowElements("rt")
            .allowElements("ruby")
            .allowElements("s")
            .allowElements("samp")
            .allowElements("section")
            .allowElements("select")
                .allowAttributes("autofocus", "disabled", "form", "multiple", "name", "required",
                        "size")
            .onElements("select")
            .allowElements("small")
            .allowElements("span")
            .allowElements("strike")
            .allowElements("strong")
            .allowElements("sub")
            .allowElements("summary")
            .allowElements("sup")
            .allowElements("table")
                .allowAttributes("align", "bgcolor", "border", "cellpadding", "cellspacing",
                        "frame", "rules", "sortable", "summary", "width", "background") //UNISOC: Modify for bug1441084
            .onElements("table")
            .allowElements("tbody")
                .allowAttributes("align", "char", "charoff", "valign").onElements("tbody")
            .allowElements("td")
                .allowAttributes("abbr", "align", "axis", "bgcolor", "char", "charoff", "colspan",
                        "height", "nowrap", "rowspan", "scope", "valign", "width")
            .onElements("td")
            .allowElements("textarea")
                .allowAttributes("autofocus", "cols", "disabled", "form", "maxlength", "name",
                        "placeholder", "readonly", "required", "rows", "wrap")
            .onElements("textarea")
            .allowElements("tfoot")
                .allowAttributes("align", "char", "charoff", "valign").onElements("tfoot")
            .allowElements("th")
                .allowAttributes("abbr", "align", "axis", "bgcolor", "char", "charoff", "colspan",
                        "height", "nowrap", "rowspan", "scope", "sorted", "valign", "width")
            .onElements("th")
            .allowElements("thead")
                .allowAttributes("align", "char", "charoff", "valign").onElements("thead")
            .allowElements("time").allowAttributes("datetime").onElements("time")
            .allowElements("tr")
                .allowAttributes("align", "bgcolor", "char", "charoff", "valign").onElements("tr")
            .allowElements("tt")
            .allowElements("u")
            .allowElements("ul").allowAttributes("compact", "type").onElements("ul")
            .allowElements("var")
            .allowElements("wbr")
            .toFactory();
    /* @} */
    /* @} */

    private HtmlSanitizer() {}

    /**
     * Sanitizing email is treated as an expensive operation; this method should be called from
     * a background Thread.
     *
     * @param rawHtml the unsanitized, suspicious html
     * @return the sanitized form of the <code>rawHtml</code>; <code>null</code> if
     *      <code>rawHtml</code> was <code>null</code>
     */
    public static String sanitizeHtml(final String rawHtml) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            throw new IllegalStateException("sanitizing email should not occur on the main thread");
        }

        if (rawHtml == null) {
            return null;
        }

        // create the builder into which the sanitized email will be written
        final StringBuilder htmlBuilder = new StringBuilder(rawHtml.length());

        // create the renderer that will write the sanitized HTML to the builder
        final HtmlStreamRenderer renderer = HtmlStreamRenderer.create(
                htmlBuilder,
                Handler.PROPAGATE,
                // log errors resulting from exceptionally bizarre inputs
                new Handler<String>() {
                    public void handle(final String x) {
                        Log.wtf(LOG_TAG, "Mangled HTML content cannot be parsed: " + x);
                        throw new AssertionError(x);
                    }
                }
        );

        // create a thread-specific policy
        final org.owasp.html.HtmlSanitizer.Policy policy = POLICY_DEFINITION.apply(renderer);

        // run the html through the sanitizer
        Timer.startTiming("sanitizingHTMLEmail");
        try {
            org.owasp.html.HtmlSanitizer.sanitize(rawHtml, policy);
        } finally {
            Timer.stopTiming("sanitizingHTMLEmail");
        }

        // return the resulting HTML from the builder
        return htmlBuilder.toString();
    }
}

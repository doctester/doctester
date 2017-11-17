/**
 * Copyright (C) 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.doctester.testbrowser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpVersion;
import org.apache.http.ParseException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.RuntimeErrorException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.doctester.testbrowser.HttpConstants.*;

public class TestBrowserImpl implements TestBrowser {

    private static Logger logger = LoggerFactory.getLogger(TestBrowserImpl.class);

    private static final String HANDLE_REDIRECTS = "http.protocol.handle-redirects";

    private DefaultHttpClient httpClient;

    public TestBrowserImpl() {
        httpClient = new DefaultHttpClient();
    }

    @Override
    public List<Cookie> getCookies() {
        return httpClient.getCookieStore().getCookies();
    }

    @Override
    public Cookie getCookieWithName(String name) {

        List<Cookie> cookies = getCookies();

        // skip through cookies and return cookie you want
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name)) {
                return cookie;
            }
        }

        return null;
    }

    @Override
    public void clearCookies() {
        httpClient.getCookieStore().clear();

    }

    @Override
    public Response makeRequest(Request httpRequest) {

        Response httpResponse;

        if (Sets.newHashSet(HEAD, GET, DELETE).contains(httpRequest.httpRequestType)) {

            httpResponse = makeHeadGetOrDeleteRequest(httpRequest);

        } else if (Sets.newHashSet(POST, PUT, PATCH).contains(httpRequest.httpRequestType)) {

            httpResponse = makePatchPostOrPutRequest(httpRequest);

        } else {

            throw new RuntimeErrorException(new Error("Your requested httpRequest.httpRequestType is not supported"));
        }

        return httpResponse;

    }

    private Response makeHeadGetOrDeleteRequest(Request request) {

        Response response;

        org.apache.http.HttpResponse apacheHttpClientResponse;

        try {

            HttpUriRequest apacheHttpRequest;

            httpClient.getParams().setParameter(
                    CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);

            if (GET.equalsIgnoreCase(request.httpRequestType)) {

                apacheHttpRequest = new HttpGet(request.uri);

            } else if (DELETE.equalsIgnoreCase(request.httpRequestType)) {

                apacheHttpRequest = new HttpDelete(request.uri);

            } else {

                apacheHttpRequest = new HttpHead(request.uri);

            }

            if (request.headers != null) {

                // add all headers
                for (Entry<String, String> header : request.headers
                        .entrySet()) {
                    apacheHttpRequest.addHeader(header.getKey(), header.getValue());
                }

            }

            setHandleRedirect(apacheHttpRequest, request.followRedirects);

            apacheHttpClientResponse = httpClient.execute(apacheHttpRequest);

            response = convertFromApacheHttpResponseToDocTesterHttpResponse(apacheHttpClientResponse);

            if (apacheHttpRequest instanceof HttpRequestBase) {
                ((HttpRequestBase) apacheHttpRequest).releaseConnection();
            }

        } catch (IOException e) {
            logger.error("Fatal problem creating GET or DELETE request in TestBrowser", e);
            throw new RuntimeException(e);
        }

        return response;
    }

    private Response makePatchPostOrPutRequest(Request httpRequest) {

        org.apache.http.HttpResponse apacheHttpClientResponse;
        Response response = null;

        try {

            httpClient.getParams().setParameter(
                    CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);

            HttpEntityEnclosingRequestBase apacheHttpRequest;

            if (PATCH.equalsIgnoreCase(httpRequest.httpRequestType)) {

                apacheHttpRequest = new HttpPatch(httpRequest.uri);

            } else if (POST.equalsIgnoreCase(httpRequest.httpRequestType)) {

                apacheHttpRequest = new HttpPost(httpRequest.uri);

            } else {

                apacheHttpRequest = new HttpPut(httpRequest.uri);
            }

            if (httpRequest.headers != null) {
                // add all headers
                for (Entry<String, String> header : httpRequest.headers
                        .entrySet()) {
                    apacheHttpRequest.addHeader(header.getKey(), header.getValue());
                }
            }

            ///////////////////////////////////////////////////////////////////
            // Either add form parameters...
            ///////////////////////////////////////////////////////////////////
            if (httpRequest.formParameters != null) {

                List<BasicNameValuePair> formparams = Lists.newArrayList();
                for (Entry<String, String> parameter : httpRequest.formParameters
                        .entrySet()) {

                    formparams.add(new BasicNameValuePair(parameter.getKey(),
                            parameter.getValue()));
                }

                // encode form parameters and add
                UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams);
                apacheHttpRequest.setEntity(entity);

            }

            ///////////////////////////////////////////////////////////////////
            // Or add multipart file upload
            ///////////////////////////////////////////////////////////////////
            if (httpRequest.filesToUpload != null) {

                MultipartEntity entity = new MultipartEntity(
                        HttpMultipartMode.BROWSER_COMPATIBLE);

                for (Map.Entry<String, File> entry : httpRequest.filesToUpload
                        .entrySet()) {

                    // For File parameters
                    entity.addPart(entry.getKey(),
                            new FileBody((File) entry.getValue()));

                }

                apacheHttpRequest.setEntity(entity);

            }

            ///////////////////////////////////////////////////////////////////
            // Or add payload and convert if Json or Xml
            ///////////////////////////////////////////////////////////////////
            if (httpRequest.payload != null) {

                if (httpRequest.headers.containsKey(HEADER_CONTENT_TYPE)
                        && httpRequest.headers.containsValue(APPLICATION_JSON_WITH_CHARSET_UTF8)) {

                    String string = new ObjectMapper().writeValueAsString(httpRequest.payload);

                    StringEntity entity = new StringEntity(string, "utf-8");
                    entity.setContentType("application/json; charset=utf-8");

                    apacheHttpRequest.setEntity(entity);

                } else if (httpRequest.headers.containsKey(HEADER_CONTENT_TYPE)
                        && httpRequest.headers.containsValue(APPLICATION_XML_WITH_CHARSET_UTF_8)) {

                    //String string = new XmlMapper().writeValueAsString(httpRequest.payload);

                    //TODO why we need to create an object and do not use it?
                    //StringEntity entity = new StringEntity(string, "utf-8");
                    //entity.setContentType(APPLICATION_XML_WITH_CHARSET_UTF_8);

                    StringWriter string = new StringWriter();
                    XMLStreamWriter xmlStreamWriter = null;
                    try {
                        xmlStreamWriter = XMLOutputFactory.newFactory().createXMLStreamWriter(string);
                    } catch (XMLStreamException e) {
                        throw new IOException(e.getMessage());
                    } finally {
                        try {
                            xmlStreamWriter.close();
                        } catch (XMLStreamException e) {
                            e.printStackTrace();
                        }
                    }

                    new XmlMapper().writeValue(xmlStreamWriter, httpRequest.payload);

                    apacheHttpRequest.setEntity(new StringEntity(string.toString(), "utf-8"));

                } else if (httpRequest.payload instanceof String) {

                    StringEntity entity = new StringEntity((String) httpRequest.payload, "utf-8");
                    apacheHttpRequest.setEntity(entity);

                } else {

                    StringEntity entity = new StringEntity(httpRequest.payload.toString(), "utf-8");
                    apacheHttpRequest.setEntity(entity);

                }

            }

            setHandleRedirect(apacheHttpRequest, httpRequest.followRedirects);

            // Here we go!
            apacheHttpClientResponse = httpClient.execute(apacheHttpRequest);
            response = convertFromApacheHttpResponseToDocTesterHttpResponse(apacheHttpClientResponse);

            apacheHttpRequest.releaseConnection();

        } catch (IOException e) {
            logger.error("Fatal problem creating PATCH, POST or PUT request in TestBrowser", e);
            throw new RuntimeException(e);
        }

        return response;

    }

    private org.doctester.testbrowser.Response convertFromApacheHttpResponseToDocTesterHttpResponse(org.apache.http.HttpResponse httpResponse) {

        Map<String, String> headers = Maps.newHashMap();

        for (Header header : httpResponse.getAllHeaders()) {

            headers.put(header.getName(), header.getValue());

        }

        int httpStatus = httpResponse.getStatusLine().getStatusCode();

        String body = null;
        HttpEntity entity = httpResponse.getEntity();
        if (entity != null) {
            try {

                body = EntityUtils.toString(entity, "UTF-8");

            } catch (IOException | ParseException e) {
                logger.error("Error while converting ApacheHttpClient response body to a String we can use", e);
            }
        }
        org.doctester.testbrowser.Response doctestJHttpResponse = new org.doctester.testbrowser.Response(
                headers, httpStatus, body);

        return doctestJHttpResponse;

    }

    /**
     * Tells ApacheHttpClient whether to follow redirects. See also:
     * http://stackoverflow.com/questions/1519392/how-to-prevent-apache-http-client-from-following-a-redirect
     */
    private void setHandleRedirect(HttpUriRequest httpUriRequest, boolean handleRedirect) {

        HttpParams params = new BasicHttpParams();
        params.setParameter(HANDLE_REDIRECTS, handleRedirect);
        httpUriRequest.setParams(params);

    }

}

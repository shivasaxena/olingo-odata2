/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 ******************************************************************************/
package org.apache.olingo.odata2.core.servlet;

import junit.framework.Assert;
import org.apache.olingo.odata2.api.ODataService;
import org.apache.olingo.odata2.api.ODataServiceFactory;
import org.apache.olingo.odata2.api.commons.HttpHeaders;
import org.apache.olingo.odata2.api.commons.HttpStatusCodes;
import org.apache.olingo.odata2.api.processor.ODataContext;
import org.apache.olingo.odata2.api.processor.ODataProcessor;
import org.apache.olingo.odata2.api.processor.ODataResponse;
import org.apache.olingo.odata2.api.servicedocument.Collection;
import org.apache.olingo.odata2.core.ODataResponseImpl;
import org.apache.olingo.odata2.core.rest.ODataServiceFactoryImpl;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.GenericServlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

/**
 *
 */
public class AbstractODataServletTest {

  private HttpServletRequest reqMock;
  private HttpServletResponse respMock;
  private ServletConfig configMock;

  public AbstractODataServletTest() {
    reqMock = Mockito.mock(HttpServletRequest.class);
    respMock = Mockito.mock(HttpServletResponse.class);
    configMock = Mockito.mock(ServletConfig.class);
  }

  private static class MyTestODataServlet extends AbstractODataServlet {
    enum ServiceFactorySource {INSTANCE, CLASSLOADER, MOCK};

    ODataServiceFactory viaInstance;
    ODataServiceFactory viaClassLoader;
    ODataServiceFactory viaMock;
    final ServiceFactorySource source;

    public MyTestODataServlet() {
      this(ServiceFactorySource.MOCK);
    }

    public MyTestODataServlet(ServiceFactorySource source) {
      this.source = source;
    }

    @Override
    protected ODataServiceFactory getServiceFactory(HttpServletRequest request) {
      viaInstance = getServiceFactoryInstance(request);
      try {
        viaClassLoader = createODataServiceFactory(request);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      viaMock = Mockito.mock(ODataServiceFactory.class);

      switch (source) {
        case INSTANCE: return viaInstance;
        case CLASSLOADER: return viaClassLoader;
        case MOCK: return viaMock;
      }
      return null;
    }
  }

  @Test
  public void handleRedirect() throws Exception {
    AbstractODataServlet servlet = new MyTestODataServlet();
    prepareServlet(servlet);
    prepareRequest(reqMock);
    servlet.service(reqMock, respMock);

    Mockito.verify(respMock).setStatus(HttpStatusCodes.TEMPORARY_REDIRECT.getStatusCode());
    Mockito.verify(respMock).setHeader(HttpHeaders.LOCATION, "/context-path/servlet-path/");
  }

  @Test
  public void handleRedirectWoServletPath() throws Exception {
    AbstractODataServlet servlet = new MyTestODataServlet();
    prepareServlet(servlet);
    prepareRequest(reqMock, "/context-path", null);
    servlet.service(reqMock, respMock);

    Mockito.verify(respMock).setStatus(HttpStatusCodes.TEMPORARY_REDIRECT.getStatusCode());
    Mockito.verify(respMock).setHeader(HttpHeaders.LOCATION, "/context-path/");
  }

  @Test
  public void handleRedirectWoContextPath() throws Exception {
    AbstractODataServlet servlet = new MyTestODataServlet();
    prepareServlet(servlet);
    prepareRequest(reqMock, null, "/servlet-path");
    servlet.service(reqMock, respMock);

    Mockito.verify(respMock).setStatus(HttpStatusCodes.TEMPORARY_REDIRECT.getStatusCode());
    Mockito.verify(respMock).setHeader(HttpHeaders.LOCATION, "/servlet-path/");
  }

  @Test
  public void handleRedirectWoPath() throws Exception {
    AbstractODataServlet servlet = new MyTestODataServlet();
    prepareServlet(servlet);
    prepareRequest(reqMock, null, null);
    servlet.service(reqMock, respMock);

    Mockito.verify(respMock).setStatus(HttpStatusCodes.TEMPORARY_REDIRECT.getStatusCode());
    Mockito.verify(respMock).setHeader(HttpHeaders.LOCATION, "/");
  }

  @Test
  public void contentLengthCalculatedString() throws Exception {
    final Method createResponse =
        AbstractODataServlet.class.getDeclaredMethod("createResponse", HttpServletResponse.class, ODataResponse.class);
    createResponse.setAccessible(true);

    final AbstractODataServlet servlet = new MyTestODataServlet();
    final String content = "Test\r\n";
    final ODataResponse response = ODataResponseImpl.status(HttpStatusCodes.OK).entity(content).build();
    prepareResponseMockToWrite(respMock);
    prepareServlet(servlet);

    createResponse.invoke(servlet, respMock, response);
    Mockito.verify(respMock).setContentLength(content.getBytes("utf-8").length);
  }

  @Test
  public void contentLengthCalculatedStream() throws Exception {
    final Method createResponse =
        AbstractODataServlet.class.getDeclaredMethod("createResponse", HttpServletResponse.class, ODataResponse.class);
    createResponse.setAccessible(true);

    final AbstractODataServlet servlet = new MyTestODataServlet();
    final String content = "Test\r\n";

    final ODataResponse response =
        ODataResponseImpl.status(HttpStatusCodes.OK).entity(new ByteArrayInputStream(content.getBytes("utf-8")))
            .build();
    prepareResponseMockToWrite(respMock);
    prepareServlet(servlet);

    createResponse.invoke(servlet, respMock, response);
    Mockito.verify(respMock).setContentLength(content.getBytes("utf-8").length);
  }

  @Test
  public void contentLengthHeader() throws Exception {
    final Method createResponse =
        AbstractODataServlet.class.getDeclaredMethod("createResponse", HttpServletResponse.class, ODataResponse.class);
    createResponse.setAccessible(true);
    final AbstractODataServlet servlet = new MyTestODataServlet();
    final ODataResponse response =
        ODataResponseImpl.status(HttpStatusCodes.OK).header(HttpHeaders.CONTENT_LENGTH, "15").entity("").build();
    prepareResponseMockToWrite(respMock);
    prepareServlet(servlet);

    createResponse.invoke(servlet, respMock, response);

    Mockito.verify(respMock).setHeader(HttpHeaders.CONTENT_LENGTH, "15");
    Mockito.verify(respMock).setContentLength(15);
  }

  @Test
  public void contentLengthHeaderInvalid() throws Exception {
    final Method createResponse =
        AbstractODataServlet.class.getDeclaredMethod("createResponse", HttpServletResponse.class, ODataResponse.class);
    createResponse.setAccessible(true);
    final AbstractODataServlet servlet = new MyTestODataServlet();
    final ODataResponse response =
        ODataResponseImpl.status(HttpStatusCodes.OK).header(HttpHeaders.CONTENT_LENGTH, "ab").entity("Test").build();
    prepareResponseMockToWrite(respMock);
    prepareServlet(servlet);

    createResponse.invoke(servlet, respMock, response);

    Mockito.verify(respMock).setHeader(HttpHeaders.CONTENT_LENGTH, "ab");
    Mockito.verify(respMock).setContentLength(4); // ||"Test"|| = 4
  }

  @Test
  public void serviceInstance() throws Exception {
    MyTestODataServlet servlet = new MyTestODataServlet(MyTestODataServlet.ServiceFactorySource.INSTANCE);
    prepareServlet(servlet, false);
    prepareRequest(reqMock, "", "/servlet-path");
    Mockito.when(reqMock.getPathInfo()).thenReturn("/request-path-info");
    Mockito.when(reqMock.getRequestURI()).thenReturn("http://localhost:8080/servlet-path/request-path-info");
    ODataServiceFactory factory = Mockito.mock(ODataServiceFactory.class);
    ODataService service = Mockito.mock(ODataService.class);
    Mockito.when(factory.createService(Mockito.any(ODataContext.class))).thenReturn(service);
    ODataProcessor processor = Mockito.mock(ODataProcessor.class);
    Mockito.when(service.getProcessor()).thenReturn(processor);
    Mockito.when(reqMock.getAttribute(ODataServiceFactory.FACTORY_INSTANCE_LABEL)).thenReturn(factory);
    Mockito.when(respMock.getOutputStream()).thenReturn(Mockito.mock(ServletOutputStream.class));

    servlet.service(reqMock, respMock);

    Mockito.verify(factory).createService(Mockito.any(ODataContext.class));
    Assert.assertNotNull(servlet.viaInstance);
    Assert.assertNull(servlet.viaClassLoader);
  }

  @Test
  public void serviceClassloader() throws Exception {
    MyTestODataServlet servlet = new MyTestODataServlet(MyTestODataServlet.ServiceFactorySource.CLASSLOADER);
    prepareServlet(servlet);
    prepareRequest(reqMock, "", "/servlet-path");
    Mockito.when(reqMock.getPathInfo()).thenReturn("/request-path-info");
    Mockito.when(reqMock.getRequestURI()).thenReturn("http://localhost:8080/servlet-path/request-path-info");
    Mockito.when(respMock.getOutputStream()).thenReturn(Mockito.mock(ServletOutputStream.class));

    servlet.service(reqMock, respMock);

    Assert.assertNull(servlet.viaInstance);
    Assert.assertNotNull(servlet.viaClassLoader);
  }

  private void prepareResponseMockToWrite(final HttpServletResponse response) throws IOException {
    Mockito.when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
      @Override
      public void write(int b) throws IOException {}
    });
  }

  private void prepareRequest(final HttpServletRequest req, final String contextPath, final String servletPath) {
    Mockito.when(req.getMethod()).thenReturn("GET");
    Mockito.when(req.getContextPath()).thenReturn(contextPath);
    Mockito.when(req.getServletPath()).thenReturn(servletPath);
    Mockito.when(req.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
  }

  private void prepareRequest(final HttpServletRequest req) {
    prepareRequest(req, "/context-path", "/servlet-path");
  }

  private void prepareServlet(final GenericServlet servlet) throws Exception {
    prepareServlet(servlet, true);
  }

  private void prepareServlet(final GenericServlet servlet, boolean withFactoryLabel) throws Exception {
    // private transient ServletConfig config;
    Field configField = GenericServlet.class.getDeclaredField("config");
    configField.setAccessible(true);
    configField.set(servlet, configMock);

    if(withFactoryLabel) {
      String factoryClassName = ODataServiceFactoryImpl.class.getName();
      Mockito.when(configMock.getInitParameter(ODataServiceFactory.FACTORY_LABEL)).thenReturn(factoryClassName);
    }
  }
}
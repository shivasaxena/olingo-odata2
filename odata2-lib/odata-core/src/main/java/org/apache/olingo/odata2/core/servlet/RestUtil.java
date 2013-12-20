package org.apache.olingo.odata2.core.servlet;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.olingo.odata2.api.exception.ODataBadRequestException;
import org.apache.olingo.odata2.api.exception.ODataException;
import org.apache.olingo.odata2.api.exception.ODataNotFoundException;
import org.apache.olingo.odata2.api.exception.ODataUnsupportedMediaTypeException;
import org.apache.olingo.odata2.api.uri.PathInfo;
import org.apache.olingo.odata2.api.uri.PathSegment;
import org.apache.olingo.odata2.core.ODataPathSegmentImpl;
import org.apache.olingo.odata2.core.PathInfoImpl;
import org.apache.olingo.odata2.core.commons.ContentType;
import org.apache.olingo.odata2.core.commons.Decoder;

public class RestUtil {
  private static final String REG_EX_QUALITY_FACTOR = "q=((?:1\\.0{0,3})|(?:0\\.[0-9]{0,2}[1-9]))";
  private static final String REG_EX_OPTIONAL_WHITESPACE = "\\s?";
  private static final Pattern REG_EX_ACCEPT = Pattern.compile("((?:[a-z\\*\\s]+/[a-zA-Z\\+\\*\\-=\\s]+"
      + "(?:;\\s*[a-pr-zA-PR-Z][a-zA-Z0-9\\-\\s]*=[a-zA-Z0-9\\-\\s]+)*))");
  //  private static final Pattern REG_EX_ACCEPT_WITH_Q_FACTOR = Pattern.compile(REG_EX_ACCEPT + "(?:;"
  //      + REG_EX_OPTIONAL_WHITESPACE + "q=([0-9]\\.?[0-9]{0,3}))?");
  private static final Pattern REG_EX_ACCEPT_WITH_Q_FACTOR = Pattern.compile(REG_EX_ACCEPT + "(?:;"
      + REG_EX_OPTIONAL_WHITESPACE + REG_EX_QUALITY_FACTOR + ")?");
  private static final Pattern REG_EX_ACCEPT_LANGUAGES = Pattern
      .compile("((?:[a-z]{1,8})|(?:\\*))\\-?([a-zA-Z]{1,8})?");
  private static final Pattern REG_EX_ACCEPT_LANGUAGES_WITH_Q_FACTOR = Pattern.compile(REG_EX_ACCEPT_LANGUAGES + "(?:;"
      + REG_EX_OPTIONAL_WHITESPACE + REG_EX_QUALITY_FACTOR + ")?");
  private static final Pattern REG_EX_MATRIX_PARAMETER = Pattern.compile("([^=]*)(?:=(.*))?");

  public static ContentType extractRequestContentType(final String contentType)
      throws ODataUnsupportedMediaTypeException {
    if (contentType == null || contentType.isEmpty()) {
      // RFC 2616, 7.2.1:
      // "Any HTTP/1.1 message containing an entity-body SHOULD include a
      // Content-Type header field defining the media type of that body. [...]
      // If the media type remains unknown, the recipient SHOULD treat it
      // as type "application/octet-stream"."
      return ContentType.APPLICATION_OCTET_STREAM;
    } else if (ContentType.isParseable(contentType)) {
      return ContentType.create(contentType);
    } else {
      throw new ODataUnsupportedMediaTypeException(
          ODataUnsupportedMediaTypeException.NOT_SUPPORTED_CONTENT_TYPE.addContent(contentType));
    }
  }

  public static Map<String, String> extractQueryParameters(final String queryString) {
    Map<String, String> queryParametersMap = new HashMap<String, String>();
    if (queryString != null) {
      List<String> queryParameters = Arrays.asList(Decoder.decode(queryString).split("\\&"));
      for (String param : queryParameters) {
        int indexOfEq = param.indexOf("=");
        if (indexOfEq < 0) {
          queryParametersMap.put(param, "");
        } else {
          queryParametersMap.put(param.substring(0, indexOfEq), param.substring(indexOfEq + 1));
        }
      }
    }
    return queryParametersMap;
  }

  public static List<Locale> extractAcceptableLanguage(final String acceptableLanguageHeader) {
    List<Locale> acceptLanguages = new ArrayList<Locale>();
    if (acceptableLanguageHeader != null) {
      Scanner acceptLanguageScanner = new Scanner(acceptableLanguageHeader).useDelimiter(",\\s?");
      while (acceptLanguageScanner.hasNext()) {
        if (acceptLanguageScanner.hasNext(REG_EX_ACCEPT_LANGUAGES_WITH_Q_FACTOR)) {
          acceptLanguageScanner.next(REG_EX_ACCEPT_LANGUAGES_WITH_Q_FACTOR);
          MatchResult result = acceptLanguageScanner.match();
          String language = result.group(1);
          String country = result.group(2);
          //        //double qualityFactor = result.group(2) != null ? Double.parseDouble(result.group(2)) : 1d;
          if (country == null) {
            acceptLanguages.add(new Locale(language));
          } else {
            acceptLanguages.add(new Locale(language, country));
          }
        }
      }
      acceptLanguageScanner.close();
    }
    return acceptLanguages;
  }

  public static List<String> extractAcceptHeaders(final String header) {
    TreeSet<Accept> acceptTree = getAcceptTree();
    List<String> acceptHeaders = new ArrayList<String>();
    if (header != null && !header.isEmpty()) {
      List<String> list = Arrays.asList(header.split(",\\s?"));
      for (String el : list) {
        Matcher matcher = REG_EX_ACCEPT_WITH_Q_FACTOR.matcher(el);
        if (matcher.find()) {
          String headerValue = matcher.group(1);
          double qualityFactor = matcher.group(2) != null ? Double.parseDouble(matcher.group(2)) : 1d;
          //double qualityFactor = Double.parseDouble(matcher.group(2));
          //if(!matcher.group(2).matches(REG_EX_QUALITY_FACTOR)){
          //  throw new ODataBadRequestException(ODataBadRequestException.INVALID_HEADER);
          //}
          Accept acceptHeader = new Accept(headerValue, qualityFactor);
          acceptTree.add(acceptHeader);
        }
      }
    }
    for (Accept accept : acceptTree) {
      acceptHeaders.add(accept.getValue());
    }
    return acceptHeaders;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, List<String>> extractHeaders(final HttpServletRequest req) {
    Map<String, List<String>> requestHeaders = new HashMap<String, List<String>>();
    for (Enumeration<String> headerNames = req.getHeaderNames(); headerNames.hasMoreElements();) {
      String headerName = headerNames.nextElement();
      List<String> headerValues = new ArrayList<String>();
      for (Enumeration<String> headers = req.getHeaders(headerName); headers.hasMoreElements();) {
        String value = headers.nextElement();
        headerValues.add(value);
      }
      if (requestHeaders.containsKey(headerName)) {
        requestHeaders.get(headerName).addAll(headerValues);
      } else {
        requestHeaders.put(headerName, headerValues);
      }
    }
    return requestHeaders;
  }

  public static PathInfo buildODataPathInfo(final HttpServletRequest req, final int pathSplit) throws ODataException {
    PathInfoImpl pathInfo = splitPath(req, pathSplit);

    pathInfo.setServiceRoot(buildBaseUri(req, pathInfo.getPrecedingSegments()));
    pathInfo.setRequestUri(buildRequestUri(req));
    return pathInfo;
  }

  private static URI buildBaseUri(final HttpServletRequest req, final List<PathSegment> precedingPathSegments)
      throws ODataException {
    try {
      URI baseUri;
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append(req.getContextPath()).append(req.getServletPath());
      for (final PathSegment ps : precedingPathSegments) {
        stringBuilder.append("/").append(ps.getPath());
        for (final String key : ps.getMatrixParameters().keySet()) {
          List<String> matrixParameters = ps.getMatrixParameters().get(key);
          String matrixParameterString = ";" + key + "=";
          for (String matrixParam : matrixParameters) {
            matrixParameterString += Decoder.decode(matrixParam) + ",";
          }
          stringBuilder.append(matrixParameterString.substring(0, matrixParameterString.length() - 1));
        }
      }

      String path = stringBuilder.toString();
      if (!path.endsWith("/")) {
        path = path + "/";
      }
      baseUri = new URI(req.getScheme(), null, req.getServerName(), req.getServerPort(), path, null, null);
      return baseUri;
    } catch (final URISyntaxException e) {
      throw new ODataException(e);
    }
  }

  private static URI buildRequestUri(final HttpServletRequest servletRequest) {
    URI requestUri;
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(servletRequest.getRequestURL());
    String queryString = servletRequest.getQueryString();

    if (queryString != null) {
      stringBuilder.append("?").append(queryString);
    }

    String requestUriString = stringBuilder.toString();
    requestUri = URI.create(requestUriString);
    return requestUri;
  }

  private static PathInfoImpl splitPath(final HttpServletRequest servletRequest, final int pathSplit)
      throws ODataException {
    PathInfoImpl pathInfo = new PathInfoImpl();
    List<String> precedingPathSegments;
    List<String> pathSegments;

    String pathInfoString = extractPathInfo(servletRequest);
    while (pathInfoString.startsWith("/")) {
      pathInfoString = pathInfoString.substring(1);
    }
    List<String> segments = Arrays.asList(pathInfoString.split("/", -1));

    if (pathSplit == 0) {
      precedingPathSegments = Collections.emptyList();
      pathSegments = segments;
    } else {
      if (segments.size() < pathSplit) {
        throw new ODataBadRequestException(ODataBadRequestException.URLTOOSHORT);
      }

      precedingPathSegments = segments.subList(0, pathSplit);
      final int pathSegmentCount = segments.size();
      pathSegments = segments.subList(pathSplit, pathSegmentCount);
    }

    // Percent-decode only the preceding path segments.
    // The OData path segments are decoded during URI parsing.
    pathInfo.setPrecedingPathSegment(convertPathSegmentList(precedingPathSegments));

    List<PathSegment> odataSegments = new ArrayList<PathSegment>();
    for (final String segment : pathSegments) {

      int index = segment.indexOf(";");
      if (index < 0) {
        odataSegments.add(new ODataPathSegmentImpl(segment, null));
      } else {
        String path = segment.substring(0, index);
        Map<String, List<String>> parameterMap = extractMatrixParameter(segment, index);
        throw new ODataNotFoundException(ODataNotFoundException.MATRIX.addContent(parameterMap.keySet(), path));
      }
    }
    pathInfo.setODataPathSegment(odataSegments);
    return pathInfo;
  }

  private static List<PathSegment> convertPathSegmentList(final List<String> pathSegments) {
    ArrayList<PathSegment> converted = new ArrayList<PathSegment>();
    for (final String segment : pathSegments) {
      int index = segment.indexOf(";");
      if (index == -1) {
        converted.add(new ODataPathSegmentImpl(Decoder.decode(segment), null));
      } else {
        String path = segment.substring(0, index);
        Map<String, List<String>> parameterMap = extractMatrixParameter(segment, index);
        converted.add(new ODataPathSegmentImpl(Decoder.decode(path), parameterMap));
      }
    }
    return converted;
  }

  private static Map<String, List<String>> extractMatrixParameter(final String segment, final int index) {
    List<String> matrixParameters = Arrays.asList(segment.substring(index + 1).split(";"));
    String matrixParameterName = "";
    String matrixParamaterValues = "";
    Map<String, List<String>> parameterMap = new HashMap<String, List<String>>();

    for (String matrixParameter : matrixParameters) {
      List<String> values = Arrays.asList("");
      Matcher matcher = REG_EX_MATRIX_PARAMETER.matcher(matrixParameter);
      if (matcher.find()) {
        matrixParameterName = matcher.group(1);
        matrixParamaterValues = matcher.group(2);
      }
      if (matrixParamaterValues != null) {
        values = Arrays.asList(matrixParamaterValues.split(","));
      }
      parameterMap.put(matrixParameterName, values);
    }
    return parameterMap;
  }

  private static String extractPathInfo(final HttpServletRequest servletRequest) {
    String pathInfoString;
    final String requestUri = servletRequest.getRequestURI();
    pathInfoString = requestUri;
    int index = requestUri.indexOf(servletRequest.getContextPath());

    if (index >= 0) {
      pathInfoString = pathInfoString.substring(servletRequest.getContextPath().length());
    }

    int indexServletPath = requestUri.indexOf(servletRequest.getServletPath());
    if (indexServletPath > 0) {
      pathInfoString = pathInfoString.substring(servletRequest.getServletPath().length());
    }
    return pathInfoString;
  }

  private static TreeSet<Accept> getAcceptTree() {
    TreeSet<Accept> treeSet = new TreeSet<Accept>(new Comparator<Accept>() {
      @Override
      public int compare(final Accept o1, final Accept o2) {
        if (o1.getQuality() <= o2.getQuality()) {
          return 1;
        } else {
          return -1;
        }
      }
    });
    return treeSet;
  }

  private static class Accept {
    private double quality;
    private String value;

    public Accept(final String headerValue, final double qualityFactor) {
      value = headerValue;
      quality = qualityFactor;
    }

    public String getValue() {
      return value;
    }

    public double getQuality() {
      return quality;
    }

  }

}
/*
  $Id: $
  @file AjaxRequestHandler.java
  @brief Contains the AjaxRequestHandler.java class

  @author Rahul Singh [rsingh]
*/
package com.distelli.webserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.HashMap;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AjaxRequestHandler extends RequestHandler
{
    @Inject
    private AjaxHelperMap _ajaxHelperMap;

    private static final Logger log = LoggerFactory.getLogger(AjaxRequestHandler.class);

    public AjaxRequestHandler()
    {

    }

    public WebResponse handleRequest(RequestContext requestContext)
    {
        try {
            String contentType = requestContext.getContentType();
            HTTPMethod httpMethod = requestContext.getHttpMethod();
            //POST requests should be content type application/json
            if(httpMethod != null && httpMethod == HTTPMethod.POST)
            {
                if(contentType == null || !contentType.equalsIgnoreCase(WebConstants.CONTENT_TYPE_JSON))
                    return jsonError(JsonError.BadContentType);
            }
            AjaxRequest ajaxRequest = null;
            JsonNode jsonContent = requestContext.getJsonContent();
            if(jsonContent != null)
                ajaxRequest = AjaxRequest.fromJson(jsonContent);
            else
                ajaxRequest = AjaxRequest.fromQueryParams(requestContext.getQueryParams());

            String operation = ajaxRequest.getOperation();
            if(operation == null)
                return jsonError(JsonError.UnsupportedOperation);
            AjaxHelper ajaxHelper = _ajaxHelperMap.get(operation);
            if(ajaxHelper == null)
                return jsonError(JsonError.UnsupportedOperation);
            if(!ajaxHelper.isMethodSupported(httpMethod))
                return jsonError(JsonError.UnsupportedHttpMethod);
            Object response = ajaxHelper.get(ajaxRequest);
            if(response != null)
                return toJson(response);
            //return a 404 with a not found json object if the object
            //was not found
            return toJson(new HashMap<String, String>(), 404);
        } catch(AjaxClientException ace) {
            JsonError jsonError = ace.getJsonError();
            if(jsonError != null)
                return jsonError(jsonError);
            return jsonError(JsonError.MalformedRequest);
        } catch(Throwable t) {
            log.error(t.getMessage(), t);
            return jsonError(JsonError.InternalServerError);
        }
    }
}

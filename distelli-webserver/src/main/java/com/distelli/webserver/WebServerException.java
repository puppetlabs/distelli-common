package com.distelli.webserver;

public class WebServerException extends RuntimeException
{
    private static final long serialVersionUID = 1L;
    public WebServerException()
    {

    }
    public WebServerException(String message)
    {
        super(message);
    }

    public WebServerException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public WebServerException(Throwable cause)
    {
        super(cause);
    }
}

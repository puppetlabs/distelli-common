package com.distelli.webserver;

public class WebClientException extends RuntimeException
{
    private static final long serialVersionUID = 1L;
    public WebClientException()
    {

    }

    public WebClientException(String message)
    {
        super(message);
    }

    public WebClientException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public WebClientException(Throwable cause)
    {
        super(cause);
    }
}

package org.sakaiproject.nakamura.email.outgoing;


public class EmailDeliveryException extends Exception {

  public EmailDeliveryException(String message) {
    super(message);
  }

  public EmailDeliveryException(String message, Throwable e) {
    super(message, e);
  }

  /**
   * 
   */
  private static final long serialVersionUID = 3264431277922630620L;

}

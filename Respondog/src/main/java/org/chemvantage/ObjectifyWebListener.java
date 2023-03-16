package org.chemvantage;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import com.googlecode.objectify.ObjectifyService;

@WebListener
public class ObjectifyWebListener implements ServletContextListener {

  @Override
  public void contextInitialized(ServletContextEvent event) {
    ObjectifyService.init();
    // This is a good place to register your POJO entity classes.
    ObjectifyService.register(Assignment.class);
    ObjectifyService.register(Deployment.class);
    ObjectifyService.register(Nonce.class);
    ObjectifyService.register(PollTransaction.class);
    ObjectifyService.register(Question.class);
    ObjectifyService.register(RSAKeyPair.class);
    ObjectifyService.register(Score.class);
    ObjectifyService.register(Subject.class);
    ObjectifyService.register(User.class);
    
    ObjectifyService.begin();
	
  }
	
  @Override
  public void contextDestroyed(ServletContextEvent event) {
  }
}
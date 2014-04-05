package com.geeksville.dapi.auth

import com.geeksville.dapi.DroneHubStack
import com.ridemission.rest.JString

class SessionsController extends DroneHubStack {

  before("/login") {
    logger.info("SessionsController: checking whether to run RememberMeStrategy: " + !isAuthenticated)

    // If not already authed, try to auth implicitly using their cookie
    if (!isAuthenticated) {
      scentry.authenticate("RememberMe")
    }
  }

  /*
    WE don't talk html
    
    get("/new") {
    if (isAuthenticated) redirect("/")
    contentType = "text/html"
    ssp("/sessions/new")
    }
    */

  post("/login") {
    scentry.authenticate()

    // User just tried to login - if login has failed tell them they can't access the site
    if (!isAuthenticated)
      haltForbidden()
    /*
    If we were using HTML this is what we would give
    
    if (isAuthenticated) {
      redirect("/")
    } else {
      redirect("/sessions/new")
    }
    * */
  }

  // Never do this in a real app. State changes should never happen as a result of a GET request. However, this does
  // make it easier to illustrate the logout code.
  post("/logout") {
    scentry.logout()

    // NOT USING HTML
    // redirect("/")
    JString("Logged out")
  }

}
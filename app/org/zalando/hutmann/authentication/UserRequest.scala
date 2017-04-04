package org.zalando.hutmann.authentication

import play.api.mvc.{ Request, WrappedRequest }

class UserRequest[A](val user: Either[AuthorizationProblem, User], request: Request[A]) extends WrappedRequest[A](request)

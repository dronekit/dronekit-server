# TODO

* Apply tridge comments
* Investigate mavproxy internals
* Limit akka internal queue lengths
* Create a vehicle actor api object (used for zeromq, direct akka tests, anything)

# Long term TODO

* Use SASL? http://docs.oracle.com/javase/1.5.0/docs/guide/security/sasl/sasl-refguide.html

# Discarded ideas

* Create two actor systems TrustedSystem and UntrustedSystem in server (per http://doc.akka.io/docs/akka/2.2.3/java/remoting.html ).  Only public API objects are in the untrusted system.
* Populate that system with a PublicVehicleAPI actor.  Initially all vehicles will call into this restricted actor.  Eventually a non akka based API
will use this object.


# TODO

* Apply tridge comments
* Investigate mavproxy internals
* Kill TCP actor when client disconnects
* create new flight log records as flight is received

# Medium term fixme

* Bail on client if his first message is not to log in
* Validate logins
* Validate access to vehicles
* Use sequence # to check for & report dropped packets

# Long term TODO

* Use SASL? http://docs.oracle.com/javase/1.5.0/docs/guide/security/sasl/sasl-refguide.html
* change mavlink parsing (MAVLinkReader) to use ByteStrings rather than byte arrays - to make for less heap allocs

# Discarded ideas

* Create two actor systems TrustedSystem and UntrustedSystem in server (per http://doc.akka.io/docs/akka/2.2.3/java/remoting.html ).  Only public API objects are in the untrusted system.
* Populate that system with a PublicVehicleAPI actor.  Initially all vehicles will call into this restricted actor.  Eventually a non akka based API
will use this object.


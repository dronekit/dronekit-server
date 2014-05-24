echo "Changing loopback device to be slow by simulating network delays"
echo "Per http://www.linuxfoundation.org/collaborate/workgroups/networking/netem"

sudo tc qdisc add dev lo root netem delay 100ms 20ms distribution normal
sudo tc qdisc


echo "Undoign change by simulating network delays"
echo "Per http://www.linuxfoundation.org/collaborate/workgroups/networking/netem"

sudo tc qdisc del dev lo root netem delay 100ms 20ms distribution normal
sudo tc qdisc


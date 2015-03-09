#javac -d target CS425_MP1/src/Node.java CS425_MP1/src/ServerThread.java CS425_MP1/src/Client.java

gnome-terminal --tab -e "java -cp CS425_MP1/bin mp1.Node A" --tab -e "java -cp CS425_MP1/bin mp1.Node B" --tab -e "java -cp CS425_MP1/bin mp1.Node C" --tab -e "java -cp CS425_MP1/bin mp1.Node D" --tab -e "java -cp CS425_MP1/bin mp1.CentralServer"

#--------------
#Killing port usages (Ctrl+C to prevent ports from getting stuck rather than CTRL+Z)
#netstat -anp|grep :8080[[:blank:]]
#kill -9 PID

/*
 * server.c -- a stream socket server demo
 * To compile: "gcc -c server.c; gcc server.o -o server"
 * To run: "./server [server_label]"
 *         Server labels can be A, B, C, D
 * To connect from same machine, different terminal: "telnet localhost 3490"
 */

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <fstream>
#include <iostream>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <sys/wait.h>
#include <signal.h>
#include <string>
#include <vector>

//#define PORTA "3490"
//#define PORTB "3491"

#define BACKLOG 10	 //how many pending connections queue will hold

using namespace std;

struct serverNode {
	char* NODE_ID;
	string IP;
	string PORT;
	int MAX_DELAY;
	
	serverNode() : IP(""), PORT(""), MAX_DELAY(-1)
	{
	}
};


serverNode* self;
vector<serverNode*> servers;


void sigchld_handler(int s)
{
	while(waitpid(-1, NULL, WNOHANG) > 0);
}

// get sockaddr, IPv4 or IPv6:
void *get_in_addr(struct sockaddr *sa)
{
	if (sa->sa_family == AF_INET) {
		return &(((struct sockaddr_in*)sa)->sin_addr);
	}

	return &(((struct sockaddr_in6*)sa)->sin6_addr);
}

		

int main(int argc, char *argv[])
{
	int sockfd, new_fd;  //listen on sockfd, new connection on new_fd
	struct addrinfo hints, *servinfo, *p;
	struct sockaddr_storage their_addr; //connector's address information
	socklen_t sin_size;
	struct sigaction sa;
	int yes=1;
	char s[INET6_ADDRSTRLEN];
	int rv;
	//char* port;

	if (argc != 2 || (strcmp(argv[1],"A") && strcmp(argv[1],"B")) ) {
		fprintf(stderr, "usage: ./server [server_label]\nServer labels can be A or B\n");
		exit(1);
	}

	self = new serverNode();
	self->NODE_ID = (argv[1]).at(0);
	cout << "This server's ID is: " << *(self->NODE_ID);
	/*
	if (strcmp(argv[1], "B") == 0)
		port = PORTB;
	else port = PORTA;*/

	/*
	 * Parse config file
	 *
	 */
	ifstream config_file;  
    config_file.open("config", ios::in);
    if(!config_file)
    {
        cout << "Error: could not open config file" << endl;
		exit(1);
    } 

	string line;

	if(getline(config_file, line) == NULL) {
		cout << "Error: no lines read from config file" << endl;
		exit(1);
	}

	//	int configKeyCount = 3; //How many data we need to get from config per server
	
	while(getline(config_file, line))
	{
		//Remove whitespace
		line.erase (std::remove (line.begin(), line.end(), ' '), line.end());
		
		
		serverNode* curNode = NULL;
		
		//Determine what serverNode this line is for
		if(line.at(0)!=self->NODE_ID) {
			for(vector<serverNode*>::iterator it = servers.begin() ; it!=servers.end(); ++it)
				if (it.NODE_ID == line.at(0)) {
					curNode = it;
					break;
				}
			
			//If no existing node found, create new serverNode with new ID
			if (curNode==NULL) {
				curNode = new serverNode();
				curNode->NODE_ID = line.at(0);
				servers.add(curNode);
			}
		}
		else
			curNode = self;
		
		if (curNode->IP=="") {
			curNode->IP = line.substr(2,line.length-2);
			cout<<"IP? : " << curNode->IP << endl;
		}		
		else if (curNode->PORT=="") {
			curNode->PORT = line.substr(2,line.length-2);
			cout<<"PORT: " << curNode->PORT << endl;
		}
		else if (curNode->MAX_DELAY==-1) {
			curNode->MAX_DELAY = atoi(line.substr(2,line.length-2), NULL);
			cout<<"MAX_DELAY: " << curNode->MAX_DELAY << endl;
		}
		else
			cout<<"Found extra undefined field for a node config" <<endl;
	}
	//////////////////End parsing
	
	memset(&hints, 0, sizeof hints);
	hints.ai_family = AF_UNSPEC;
	hints.ai_socktype = SOCK_STREAM;
	hints.ai_flags = AI_PASSIVE; //use my IP

	if ((rv = getaddrinfo(NULL, self->PORT, &hints, &servinfo)) != 0) {
		fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(rv));
		return 1;
	}

	//loop through all the results and bind to the first we can
	for(p = servinfo; p != NULL; p = p->ai_next) {
		if ((sockfd = socket(p->ai_family, p->ai_socktype,
				p->ai_protocol)) == -1) {
			perror("server: socket");
			continue;
		}

		if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &yes,
				sizeof(int)) == -1) {
			perror("setsockopt");
			exit(1);
		}

		if (bind(sockfd, p->ai_addr, p->ai_addrlen) == -1) {
			close(sockfd);
			perror("server: bind");
			continue;
		}

		break;
	}

	if (p == NULL)  {
		fprintf(stderr, "server: failed to bind\n");
		return 2;
	}

	freeaddrinfo(servinfo); //losing server info structure

	if (listen(sockfd, BACKLOG) == -1) {
		perror("listen");
		exit(1);
	}

	sa.sa_handler = sigchld_handler; //reap all dead processes
	sigemptyset(&sa.sa_mask); // created when processes are forked
	sa.sa_flags = SA_RESTART; // and done being used
	if (sigaction(SIGCHLD, &sa, NULL) == -1) {
		perror("sigaction");
		exit(1);
	}

	printf("server: waiting for connections...\n");

	while(1) {  //main accept() loop
		sin_size = sizeof their_addr;
		new_fd = accept(sockfd, (struct sockaddr *)&their_addr, &sin_size);
		if (new_fd == -1) {
			perror("accept");
			continue;
		}

		inet_ntop(their_addr.ss_family,
			get_in_addr((struct sockaddr *)&their_addr),
			s, sizeof s);
		printf("server: got connection from %s\n", s);

		if (!fork()) { // this is the child process
			close(sockfd); //child doesn't need the listener
			if (send(new_fd, "Hello, world!", 13, 0) == -1)
				perror("send");
			close(new_fd);
			exit(0);
		}
		close(new_fd); //parent doesn't need this; closing connection to client
	}

	return 0;
}


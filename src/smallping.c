#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/ip_icmp.h>
#include <arpa/inet.h>

#include <stdlib.h>

#include <unistd.h>
#include <sys/time.h>
#include <stdio.h>
#include <sys/select.h>
#include <strings.h>
#include <fcntl.h>
#include <time.h>

#define MAX_PACKET 100

#define IP_SIZE 20
#define ICMP_SIZE 8

#define NUM_PING 20

unsigned short cksum(unsigned short * addr, int len)
{
    int nleft = len;
    int sum = 0;
    unsigned short *w = addr;
    unsigned short answer = 0;
    while (nleft >1) {
        sum = sum + *w++; nleft = nleft - 2;
    }
    if (nleft == 1) {
        *(unsigned char *) (&answer) = * (unsigned char *) w; sum = sum + answer;
    }
    sum = (sum >> 16) + (sum & 0xffff);
    sum = sum + (sum >> 16);
    answer = ~sum;
    return answer;
}

double tv_sub(const struct timeval *out, const struct timeval *in)
{
    time_t delta_usec;
    suseconds_t delta_sec;
    
    delta_usec = out->tv_usec - in->tv_usec;
    delta_sec = out->tv_sec - in->tv_sec;
    if (delta_usec < 0) {
        delta_sec--;
        delta_usec += 1000000;
    }
    return ((double) delta_sec) + ((double) delta_usec / 1000000L);
}

int received(int icmp_sock, int echo_id, double* delays) {
    char pkt_buf[MAX_PACKET];
    struct icmp * icmp_buf = (struct icmp *) pkt_buf;
    double delay;
    
    bzero(&pkt_buf, MAX_PACKET);
    int read = recvfrom(icmp_sock, pkt_buf, MAX_PACKET, 0, NULL, NULL);
    if(read == -1)
        return 0;
    icmp_buf = pkt_buf + IP_SIZE;
    
    //Check if it's one of our ping reply
    if(icmp_buf->icmp_type == ICMP_ECHOREPLY && icmp_buf->icmp_id == echo_id && icmp_buf->icmp_seq < NUM_PING) {
        struct timeval tvrec;
        gettimeofday((struct timeval *) &tvrec, NULL);
        delay = tv_sub(&tvrec, (struct timeval *) &icmp_buf->icmp_data);
        delays[icmp_buf->icmp_seq] =  delay;
        return 1;
    }
    return 0;
}

int reset_select(int socket, struct timeval *timeout) {
    fd_set readset;    
    FD_ZERO(&readset);
    FD_SET(socket, &readset);
    return select(socket + 1, &readset, NULL, NULL, timeout);
}

int
main(int argc, char *argv[])
{
    if(argc != 2 ) {
        printf("N:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U\n");
        exit(1);
    }
    char pkt_buf[MAX_PACKET];
    struct icmp * icmp_buf = (struct icmp *) pkt_buf;
    
    double delays[NUM_PING];
    
    int icmp_sock;
    icmp_sock=socket(AF_INET, SOCK_RAW, IPPROTO_ICMP);
    if (icmp_sock == -1) {
        perror(NULL);
        exit(1);
    }
    
    fcntl(icmp_sock, F_SETFL, O_NONBLOCK);
    
    struct sockaddr_in icmp_sock_info;
    int lsock = sizeof(icmp_sock_info);
    
    struct in_addr addr;    
    icmp_sock_info.sin_family= AF_INET;
    
    // just in case, filter the ip. This code is setuid
    char ip_buff[16];
    strncpy(ip_buff, argv[1], 15);
    ip_buff[15] = '\0';
    if(inet_aton(argv[1], &addr) == 0) {
        printf("N:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U\n");
        exit(1);
    }
    icmp_sock_info.sin_addr.s_addr=addr.s_addr;
    
    int echo_id = getpid();
    
    int ping_out = 0;
    
    struct timeval timeout;
    
    int result;
    int nseq;
    
    bzero(&pkt_buf, MAX_PACKET);
    icmp_buf->icmp_type=ICMP_ECHO;
    icmp_buf->icmp_code=0;
    icmp_buf->icmp_id=echo_id;
    
    for(nseq = 0; nseq < NUM_PING; nseq++) {
        delays[nseq] = -1;
        icmp_buf->icmp_seq=nseq;
        gettimeofday((struct timeval *) &icmp_buf->icmp_data, NULL);
        //cksum must be null for checksum calculation
        icmp_buf->icmp_cksum=0;
        icmp_buf->icmp_cksum=cksum((unsigned short *) icmp_buf, ICMP_SIZE + sizeof(struct timeval));
        
        sendto(icmp_sock, icmp_buf, ICMP_SIZE  + sizeof(struct timeval), 0, (struct sockaddr *) &icmp_sock_info, lsock);
        ping_out++;
        
        // wait a little, so packet are not send to fast
        // but wait with a select, in case a packet come back
        timeout.tv_sec = 0;
        timeout.tv_usec = 10000;
        while((result = reset_select(icmp_sock, &timeout)) > 0) {
            while(received(icmp_sock, echo_id, delays) > 0) {
                ping_out--;
            }
        }
    }
    
    timeout.tv_sec = 5;
    timeout.tv_usec = 0;
    
    struct timeval end_wait;
    gettimeofday((struct timeval *) &end_wait, NULL);
    end_wait.tv_sec += timeout.tv_sec;
    
    //Wait for missing packets
    while(ping_out > 0) {
        result = reset_select(icmp_sock, &timeout);
        if ( result > 0) {
            while ( received(icmp_sock, echo_id, delays) > 0) {
                ping_out--;
            }
        }
        else {
            if (result == -1) {
                perror(NULL);
            }
            // time out or failed
            break;
        }
        //calculate time left from start of wait
        struct timeval time_received;
        gettimeofday((struct timeval *) &time_received, NULL);
        
        timeout.tv_sec = end_wait.tv_sec - time_received.tv_sec;
        timeout.tv_usec = end_wait.tv_usec - time_received.tv_usec;
        if( timeout.tv_usec < 0) {
            timeout.tv_sec--;
            timeout.tv_usec += 1000000;
        }
        //Nothing left to wait, hang over
        if(timeout.tv_sec <= 0 && timeout.tv_usec <= 0) {
            break;
        }
    }
    printf("N");
    for(nseq = 0; nseq < NUM_PING; nseq++) {
        if(delays[nseq] >= 0) {
            printf(":%e", delays[nseq]);
        }
        else {
            printf(":U");
        }
    }
    printf("\n");
    exit(0);
}
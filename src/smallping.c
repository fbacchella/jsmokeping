/* On Linux, _DEFAULT_SOURCE (or the older _BSD_SOURCE) is required to get
 * bzero() and other BSD-ish helpers declared when compiling with a strict
 * standard (e.g. -std=c11). Defining it here is harmless on macOS. */
#ifndef _DEFAULT_SOURCE
#define _DEFAULT_SOURCE
#endif

#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/ip_icmp.h>
#include <arpa/inet.h>

#include <stdint.h>
#include <stdlib.h>

#include <unistd.h>
#include <sys/time.h>
#include <stdio.h>
#include <sys/select.h>
#include <strings.h>
#include <fcntl.h>
#include <time.h>
#include <errno.h>

#define MAX_PACKET 100

#define IP_SIZE 20
#define ICMP_SIZE 8

#define NUM_PING 20

/* On Linux, unprivileged "ping" sockets (SOCK_DGRAM + IPPROTO_ICMP) never
 * include the IP header in the data returned by recvfrom(): the kernel
 * strips it before delivery. On macOS/BSD, the same socket type still
 * hands back the IP header exactly like a SOCK_RAW socket would. This
 * only matters when we actually end up using a SOCK_DGRAM socket (see
 * main()); SOCK_RAW always includes the IP header on every platform. */
#if defined(__linux__)
#define DGRAM_HAS_IP_HEADER 0
#else
#define DGRAM_HAS_IP_HEADER 1
#endif

/* The BSD "struct icmp" (with its icmp_id/icmp_seq/icmp_data macros) is
 * provided by <netinet/ip_icmp.h> on macOS and other BSD derived systems,
 * but glibc on Linux only exposes "struct icmphdr" there, with a different
 * layout and field names. To keep this file compiling unmodified on both
 * systems, we define our own minimal ICMP echo header instead of relying
 * on the OS one. The ICMP_ECHO/ICMP_ECHOREPLY constants used below are
 * plain integers defined on both platforms, so <netinet/ip_icmp.h> is
 * still included for them. On BSD-derived systems that header also
 * defines icmp_id/icmp_seq/icmp_data as macros aliasing "struct icmp"
 * members; undefine them so they don't clash with our own field names. */
#ifdef icmp_id
#undef icmp_id
#endif
#ifdef icmp_seq
#undef icmp_seq
#endif
#ifdef icmp_data
#undef icmp_data
#endif
/* No packing attribute is required here: type(1)+code(1)+cksum(2)+id(2)
 * +seq(2) already add up to 8 bytes, which is a multiple of the alignment
 * required by "struct timeval", so icmp_data naturally starts right after
 * icmp_seq with no padding on every platform we care about. */
struct icmp_echo {
    uint8_t  icmp_type;
    uint8_t  icmp_code;
    uint16_t icmp_cksum;
    uint16_t icmp_id;
    uint16_t icmp_seq;
    struct timeval icmp_data;
};

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

int received(int icmp_sock, int echo_id, double* delays, struct in_addr expected_addr, int ip_header_size) {
    char pkt_buf[MAX_PACKET];
    struct icmp_echo * icmp_buf = (struct icmp_echo *) pkt_buf;
    double delay;
    struct sockaddr_in from_addr;
    socklen_t from_len = sizeof(from_addr);

    bzero(&pkt_buf, MAX_PACKET);
    bzero(&from_addr, sizeof(from_addr));
    int nread = recvfrom(icmp_sock, pkt_buf, MAX_PACKET, 0, (struct sockaddr *) &from_addr, &from_len);
    if(nread == -1)
        return 0;
    if(ip_header_size > 0) {
        //Not even enough data to read the IP header length field: reject.
        if(nread < 1)
            return 0;
        //The IP header length is only reliable for headers without options
        //(IHL == 5, i.e. 20 bytes): a packet with IP options would shift the
        //real start of the ICMP payload past our fixed ip_header_size offset.
        //Rather than risk misreading such a packet, reject it outright as an
        //error, without any further processing.
        if(((pkt_buf[0] & 0x0F) * 4) != ip_header_size)
            return 0;
    }
    //Discard truncated packets: not enough room for the (possibly absent) IP
    //header, our ICMP header and the embedded timeval we use to compute the
    //delay.
    if((size_t) nread < (size_t) ip_header_size + ICMP_SIZE + sizeof(struct timeval))
        return 0;
    //Discard replies that didn't come from the host we pinged, so a third
    //party can't forge replies and pollute the measurements.
    if(from_addr.sin_addr.s_addr != expected_addr.s_addr)
        return 0;
    icmp_buf = (struct icmp_echo *) (pkt_buf + ip_header_size);

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
    struct icmp_echo * icmp_buf = (struct icmp_echo *) pkt_buf;
    
    double delays[NUM_PING];
    
    int icmp_sock;
    int using_dgram = 0;
    //Unprivileged "ping" sockets (SOCK_DGRAM+IPPROTO_ICMP) let a non-root
    //user send/receive ICMP echo without setuid. There is no point trying
    //this when we are already root (e.g. through the setuid bit on this
    //binary): SOCK_RAW always works for root, and some systems (OpenBSD,
    //or Linux without net.ipv4.ping_group_range configured) don't support
    //SOCK_DGRAM ICMP sockets at all, so we fall back to SOCK_RAW whenever
    //we didn't even try SOCK_DGRAM, or it failed.
    if (getuid() != 0) {
        icmp_sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP);
        if (icmp_sock != -1) {
            using_dgram = 1;
        }
    }
    if (!using_dgram) {
        icmp_sock=socket(AF_INET, SOCK_RAW, IPPROTO_ICMP);
    }
    if (icmp_sock == -1) {
        perror(NULL);
        exit(1);
    }
    
    fcntl(icmp_sock, F_SETFL, O_NONBLOCK);
    
    //See the DGRAM_HAS_IP_HEADER comment above: on Linux, SOCK_DGRAM ICMP
    //sockets never hand back the IP header, so there is nothing to skip
    //when parsing a reply.
    int ip_header_size = IP_SIZE;
#if !DGRAM_HAS_IP_HEADER
    if (using_dgram) {
        ip_header_size = 0;
    }
#endif
    
    struct sockaddr_in icmp_sock_info;
    int lsock = sizeof(icmp_sock_info);
    bzero(&icmp_sock_info, sizeof(icmp_sock_info));
    
    struct in_addr addr;    
    icmp_sock_info.sin_family= AF_INET;
    
    // just in case, filter the ip. This code is setuid
    if(inet_aton(argv[1], &addr) == 0) {
        printf("N:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U:U\n");
        exit(1);
    }
    icmp_sock_info.sin_addr.s_addr=addr.s_addr;
    
    //A random id (rather than the pid truncated to 16 bits) avoids two
    //processes started with the same low 16 bits of their pid colliding
    //when they happen to ping the same host at the same time.
    struct timeval seed_tv;
    gettimeofday(&seed_tv, NULL);
    srandom((unsigned int) (seed_tv.tv_sec ^ seed_tv.tv_usec ^ getpid()));
    uint16_t echo_id = random() & 0xFFFF;
    
#if !DGRAM_HAS_IP_HEADER
    if (using_dgram) {
        //On Linux, a "ping" socket's ICMP id is actually the socket's own
        //local port: the kernel ignores whatever id we put in outgoing
        //packets and rewrites it to this port, using it to route replies
        //back to us. Bind explicitly to our chosen echo_id so it stays the
        //id we expect in received(); if that port happens to be taken,
        //let the kernel assign one instead and read back whichever id it
        //picked.
        struct sockaddr_in bind_addr;
        bzero(&bind_addr, sizeof(bind_addr));
        bind_addr.sin_family = AF_INET;
        bind_addr.sin_port = htons(echo_id);
        if (bind(icmp_sock, (struct sockaddr *) &bind_addr, sizeof(bind_addr)) == -1) {
            //Our random id happened to collide with another socket's port:
            //let the kernel pick one for us instead.
            bind_addr.sin_port = 0;
            bind(icmp_sock, (struct sockaddr *) &bind_addr, sizeof(bind_addr));
        }
        //The kernel copies this raw 16 bit port value verbatim into octets
        //4-5 (the id field) of every outgoing/incoming ICMP packet on this
        //socket, without ever applying ntohs/htons to it again. echo_id
        //must therefore be compared against that same raw representation,
        //not against the (unconverted) value we originally picked.
        socklen_t bind_addr_len = sizeof(bind_addr);
        if (getsockname(icmp_sock, (struct sockaddr *) &bind_addr, &bind_addr_len) == 0) {
            echo_id = bind_addr.sin_port;
        }
    }
#endif
    
    int ping_out = 0;
    
    struct timeval timeout;
    
    int result;
    int nseq;
    //Set when a signal interrupts select(): processing is stopped right
    //away, but the final status line is still printed below, with a U for
    //every packet that hasn't got a reply yet.
    int interrupted = 0;
    
    bzero(&pkt_buf, MAX_PACKET);
    icmp_buf->icmp_type=ICMP_ECHO;
    icmp_buf->icmp_code=0;
    icmp_buf->icmp_id=echo_id;
    
    for(nseq = 0; nseq < NUM_PING && !interrupted; nseq++) {
        delays[nseq] = -1;
        icmp_buf->icmp_seq=nseq;
        gettimeofday((struct timeval *) &icmp_buf->icmp_data, NULL);
        //cksum must be null for checksum calculation
        icmp_buf->icmp_cksum=0;
        icmp_buf->icmp_cksum=cksum((unsigned short *) icmp_buf, ICMP_SIZE + sizeof(struct timeval));
        
        //Only count the packet as "out" if it was actually sent, so a
        //failed sendto() doesn't skew the final count/wait.
        if(sendto(icmp_sock, icmp_buf, ICMP_SIZE  + sizeof(struct timeval), 0, (struct sockaddr *) &icmp_sock_info, lsock) >= 0) {
            ping_out++;
        }
        
        // wait a little, so packet are not send to fast
        // but wait with a select, in case a packet come back
        timeout.tv_sec = 0;
        timeout.tv_usec = 10000;
        errno = 0;
        while((result = reset_select(icmp_sock, &timeout)) > 0) {
            while(received(icmp_sock, echo_id, delays, addr, ip_header_size) > 0) {
                ping_out--;
            }
        }
        if(result == -1 && errno == EINTR) {
            interrupted = 1;
        }
    }
    
    timeout.tv_sec = 5;
    timeout.tv_usec = 0;
    
    struct timeval end_wait;
    gettimeofday((struct timeval *) &end_wait, NULL);
    end_wait.tv_sec += timeout.tv_sec;
    
    //Wait for missing packets
    while(!interrupted && ping_out > 0) {
        errno = 0;
        result = reset_select(icmp_sock, &timeout);
        if ( result > 0) {
            while (received(icmp_sock, echo_id, delays, addr, ip_header_size) > 0) {
                ping_out--;
            }
        }
        else {
            if (result == -1) {
                if (errno == EINTR) {
                    interrupted = 1;
                }
                else {
                    perror(NULL);
                }
            }
            // time out, failed or interrupted: stop waiting, the status
            // line below will report 'U' for every packet still missing
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

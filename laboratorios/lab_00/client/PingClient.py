import socket
import time
import sys


NUMBER_OF_PINGS = 10
TIMEOUT = 1


def main():

    if len(sys.argv) != 3:
        print("Uso: python PingClient.py host porta")
        return


    host = sys.argv[1]
    port = int(sys.argv[2])


    client_socket = socket.socket(
        socket.AF_INET,
        socket.SOCK_DGRAM
    )


    client_socket.settimeout(TIMEOUT)


    lost_packets = 0


    rtts = []


    for sequence in range(NUMBER_OF_PINGS):


        timestamp = int(time.time() * 1000)


        message = (
            f"PING {sequence} {timestamp}\r\n"
        )


        send_time = time.time()


        client_socket.sendto(
            message.encode(),
            (host, port)
        )


        try:

            data, server = client_socket.recvfrom(1024)


            receive_time = time.time()


            rtt = (
                receive_time - send_time
            ) * 1000


            rtts.append(rtt)


            print(
                f"Resposta: {data.decode().strip()}"
            )

            print(
                f"RTT: {rtt:.2f} ms\n"
            )


        except socket.timeout:

            lost_packets += 1

            print(
                f"Timeout: pacote {sequence} perdido\n"
            )


        time.sleep(1)



    print("====================")

    print(
        f"Pacotes perdidos: {lost_packets}/{NUMBER_OF_PINGS}"
    )


    if rtts:

        print(
            f"RTT mínimo: {min(rtts):.2f} ms"
        )

        print(
            f"RTT máximo: {max(rtts):.2f} ms"
        )

        print(
            f"RTT médio: {sum(rtts)/len(rtts):.2f} ms"
        )


    client_socket.close()



if __name__ == "__main__":
    main()
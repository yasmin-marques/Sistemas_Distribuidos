import socket
import random
import time


LOSS_RATE = 0.3
AVERAGE_DELAY = 0.1


def main():

    if len(__import__('sys').argv) != 2:
        print("Uso: python PingServer.py porta")
        return


    port = int(__import__('sys').argv[1])


    server_socket = socket.socket(
        socket.AF_INET,
        socket.SOCK_DGRAM
    )


    server_socket.bind(
        ("", port)
    )


    print(f"Servidor UDP iniciado na porta {port}")


    while True:

        data, client_address = server_socket.recvfrom(1024)


        print(
            f"Recebido de {client_address}: {data.decode()}"
        )


        # simula perda de pacote
        if random.random() < LOSS_RATE:

            print("Resposta não enviada (perda simulada)")
            continue


        # simula atraso de rede
        time.sleep(
            random.random() * 2 * AVERAGE_DELAY
        )


        server_socket.sendto(
            data,
            client_address
        )


        print("Resposta enviada")


if __name__ == "__main__":
    main()
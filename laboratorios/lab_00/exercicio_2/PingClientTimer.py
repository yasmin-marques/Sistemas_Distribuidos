import socket
import time
import sys
import threading


# Exercicio 01: Resolvido no Client/PingClient.py
# Exercicio 02: PingClientTimer.py

NUMBER_OF_PINGS = 10
INTERVAL = 1.0   
TIMEOUT = 1.0  


def main():

    if len(sys.argv) != 3:
        print("Uso: python PingClientTimer.py host porta")
        return

    host = sys.argv[1]
    port = int(sys.argv[2])

    client_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    client_socket.settimeout(0.2)

    send_times = {}    
    rtts = {}            
    lock = threading.Lock()
    stop_event = threading.Event()

    def receiver():
        """Roda em paralelo aos envios, recebendo respostas assim que chegam."""
        while not stop_event.is_set():
            try:
                data, _ = client_socket.recvfrom(1024)
            except socket.timeout:
                continue
            except OSError:
                break

            receive_time = time.time()

            try:
                parts = data.decode().strip().split()
                seq = int(parts[1])
            except (IndexError, ValueError):
                continue

            with lock:
                if seq in send_times and seq not in rtts:
                    rtt = (receive_time - send_times[seq]) * 1000
                    rtts[seq] = rtt
                    print(f"Resposta: {data.decode().strip()}")
                    print(f"RTT: {rtt:.2f} ms\n")

    receiver_thread = threading.Thread(target=receiver, daemon=True)
    receiver_thread.start()

    def send_ping(sequence):
        timestamp = int(time.time() * 1000)
        message = f"PING {sequence} {timestamp}\r\n"

        with lock:
            send_times[sequence] = time.time()

        client_socket.sendto(message.encode(), (host, port))
        print(f"Ping enviado: sequence={sequence} time={timestamp}")

    timers = []
    for sequence in range(NUMBER_OF_PINGS):
        t = threading.Timer(sequence * INTERVAL, send_ping, args=(sequence,))
        timers.append(t)
        t.start()

    total_wait = (NUMBER_OF_PINGS - 1) * INTERVAL + TIMEOUT + 0.3
    time.sleep(total_wait)

    stop_event.set()
    client_socket.close()
    receiver_thread.join(timeout=1)

    lost_packets = 0

    print("------------------")

    for sequence in range(NUMBER_OF_PINGS):
        if sequence not in rtts:
            lost_packets += 1
            print(f"Timeout: pacote {sequence} perdido")

    print(f"Pacotes perdidos: {lost_packets}/{NUMBER_OF_PINGS}")

    if rtts:
        values = list(rtts.values())
        print(f"RTT mínimo: {min(values):.2f} ms")
        print(f"RTT máximo: {max(values):.2f} ms")
        print(f"RTT médio: {sum(values)/len(values):.2f} ms")


if __name__ == "__main__":
    main()
import socket
import random
import sys


class ReliableUdpReceiver:
    """Recebe dados enviados por ReliableUdpSender de forma confiavel,
    confirmando cada pacote (DATA/FIN) com um ACK e ignorando duplicatas
    (mas ainda assim reenviando o ACK, caso o ACK anterior tenha se
    perdido no caminho de volta)."""

    def __init__(self, port, loss_rate=0.0):
        self.loss_rate = loss_rate
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.socket.bind(("", port))
        self.expected_seq = 0

    def _send_ack(self, seq, sender_address):
        ack = f"ACK {seq}\r\n".encode()
        if random.random() < self.loss_rate:
            print(f"[Receiver] (perda simulada) ACK {seq} nao enviado")
            return
        self.socket.sendto(ack, sender_address)

    def receive(self) -> bytes:
        """Bloqueia ate receber a transferencia completa (ate o FIN) e
        retorna os dados recebidos, ja remontados e em ordem."""
        buffer = bytearray()

        while True:
            packet, sender_address = self.socket.recvfrom(65536)

            try:
                header, payload = packet.split(b"\r\n", 1)
            except ValueError:
                header, payload = packet, b""

            parts = header.decode(errors="ignore").split()
            if len(parts) < 2:
                continue

            msg_type, seq = parts[0], int(parts[1])

            if msg_type == "FIN":
                self._send_ack(seq, sender_address)
                if seq == self.expected_seq:
                    print("[Receiver] FIN recebido - transferencia concluida")
                    self.expected_seq += 1
                    return bytes(buffer)
                continue

            if msg_type == "DATA":
                if seq == self.expected_seq:
                    print(f"[Receiver] DATA {seq} recebido "
                          f"({len(payload)} bytes) - novo, entregando")
                    buffer.extend(payload)
                    self.expected_seq += 1
                elif seq < self.expected_seq:
                    print(f"[Receiver] DATA {seq} duplicado - reenviando ACK, "
                          f"payload descartado")
                else:
                    print(f"[Receiver] DATA {seq} fora de ordem - ignorado")
                    continue

                self._send_ack(seq, sender_address)

    def close(self):
        self.socket.close()


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Uso: python ReliableUdpReceiver.py porta [loss_rate]")
        sys.exit(1)

    port = int(sys.argv[1])
    loss_rate = float(sys.argv[2]) if len(sys.argv) > 2 else 0.3

    receiver = ReliableUdpReceiver(port, loss_rate=loss_rate)
    print(f"[Receiver] aguardando dados na porta {port} "
          f"(loss_rate={loss_rate})...")
    try:
        data = receiver.receive()
        print("---------------------------------------")
        print(f"[Receiver] dados recebidos ({len(data)} bytes):")
        print(data.decode(errors="replace"))
    finally:
        receiver.close()

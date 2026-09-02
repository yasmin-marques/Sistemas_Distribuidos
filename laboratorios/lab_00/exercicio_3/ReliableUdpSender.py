import socket
import random
import sys

# Exercicio 03: ReliableUdpSender / ReliableUdpReceiver

class ReliableUdpSender:
    """Envia dados de forma confiavel e unidirecional sobre UDP."""

    def __init__(self, host, port, timeout=1.0, max_retries=10,
                 loss_rate=0.0, chunk_size=1000):
        self.address = (host, port)
        self.timeout = timeout
        self.max_retries = max_retries
        self.loss_rate = loss_rate     
        self.chunk_size = chunk_size

        self.socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.socket.settimeout(timeout)

    def _send_simulating_loss(self, packet):
        """Simula perda de pacotes IP: as vezes o pacote nao e enviado."""
        if random.random() < self.loss_rate:
            print("[Sender] (perda simulada) pacote nao enviado")
            return
        self.socket.sendto(packet, self.address)

    def _send_reliable(self, msg_type, seq, payload=b""):
        """Envia um pacote (DATA ou FIN) e retransmite ate receber o ACK
        correspondente ou esgotar as tentativas."""
        header = f"{msg_type} {seq} {len(payload)}\r\n".encode()
        packet = header + payload

        for attempt in range(1, self.max_retries + 1):
            self._send_simulating_loss(packet)

            try:
                ack, _ = self.socket.recvfrom(1024)
            except socket.timeout:
                print(f"[Sender] timeout aguardando ACK {seq} "
                      f"(tentativa {attempt}/{self.max_retries}), retransmitindo")
                continue

            parts = ack.decode(errors="ignore").strip().split()
            if len(parts) >= 2 and parts[0] == "ACK" and int(parts[1]) == seq:
                return True

        return False

    def send(self, data: bytes) -> bool:
        """Envia `data` de forma confiavel, dividindo em pedacos de
        `chunk_size` bytes. Retorna True se toda a transferencia (incluindo
        o FIN) foi confirmada pelo receiver."""

        chunks = [data[i:i + self.chunk_size]
                  for i in range(0, len(data), self.chunk_size)]
        if not chunks:
            chunks = [b""]

        for seq, chunk in enumerate(chunks):
            print(f"[Sender] enviando DATA {seq} ({len(chunk)} bytes)")
            if not self._send_reliable("DATA", seq, chunk):
                print(f"[Sender] falha ao entregar o pacote {seq} "
                      f"apos {self.max_retries} tentativas - abortando")
                return False
            print(f"[Sender] DATA {seq} confirmado (ACK recebido)")

        fin_seq = len(chunks)
        print("[Sender] enviando FIN")
        if not self._send_reliable("FIN", fin_seq):
            print("[Sender] falha ao confirmar o FIN - abortando")
            return False

        print("[Sender] transferencia concluida com sucesso")
        return True

    def close(self):
        self.socket.close()


if __name__ == "__main__":
    if len(sys.argv) < 4:
        print('Uso: python ReliableUdpSender.py host porta "mensagem" [loss_rate]')
        sys.exit(1)

    host = sys.argv[1]
    port = int(sys.argv[2])
    message = sys.argv[3]
    loss_rate = float(sys.argv[4]) if len(sys.argv) > 4 else 0.3

    sender = ReliableUdpSender(host, port, loss_rate=loss_rate, chunk_size=20)
    try:
        sender.send(message.encode())
    finally:
        sender.close()

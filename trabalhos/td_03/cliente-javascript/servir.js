// Serve os arquivos do cliente web em http://localhost:5500 (só arquivos estáticos).
// node servir.js [porta]
// Alternativa sem Node: abrir index.html direto no navegador, ou `python -m http.server 5500`.
const http = require('http');
const fs = require('fs');
const path = require('path');

const porta = Number(process.argv[2] || 5500);
const tipos = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.json': 'application/json' };

http.createServer((req, res) => {
  const url = decodeURIComponent(req.url.split('?')[0]);
  const arquivo = path.normalize(path.join(__dirname, url === '/' ? 'index.html' : url));
  if (!arquivo.startsWith(__dirname) || arquivo.includes(`${path.sep}teste${path.sep}`)) {
    res.writeHead(403).end('Acesso negado');
    return;
  }
  fs.readFile(arquivo, (erro, dados) => {
    if (erro) return res.writeHead(404).end('Não encontrado');
    res.writeHead(200, { 'Content-Type': tipos[path.extname(arquivo)] || 'application/octet-stream' }).end(dados);
  });
}).listen(porta, () => console.log(`Cliente web em http://localhost:${porta}  (o servidor da API deve estar em http://localhost:8080)`));

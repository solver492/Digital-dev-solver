// OpenCode Mobile CLI entrypoint
console.log("[cli] OpenCode CLI initialisé.");
console.log("[cli] Arguments reçus :", process.argv.slice(2).join(" "));

const port = process.env.PORT || 4096;
const readline = require('readline');
const http = require('http');

// Serveur HTTP local pour health-check et intégration
const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({
    status: 'ok',
    agent: 'OpenCode Mobile',
    timestamp: Date.now()
  }));
});

server.listen(port, '127.0.0.1', () => {
  console.log(`[sys] Serveur OpenCode actif sur http://127.0.0.1:${port}`);
});

// Écoute des entrées interactives transmises depuis l'application
const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});

rl.on('line', (line) => {
  const text = line.trim();
  console.log(`[agent] Reçu : ${text}`);
  if (text.toLowerCase() === 'salut' || text.toLowerCase() === 'bonjour' || text.toLowerCase() === 'hello') {
    console.log("[agent] Bonjour ! OpenCode est opérationnel.");
  }
});

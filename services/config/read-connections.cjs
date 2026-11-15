// Build-time reader for the deliberately simple shared .properties format.
// Runs in Node (Vite / CDK), never in the browser. No credentials are exported by publicConnections.
const { readFileSync } = require('node:fs');
const { join } = require('node:path');

function readConnections(environment = process.env, file = join(__dirname, 'connections.properties')) {
  const properties = Object.fromEntries(readFileSync(file, 'utf8').split(/\r?\n/)
    .filter(line => line.trim() && !line.trimStart().startsWith('#'))
    .map(line => {
      const separator = line.indexOf('=');
      if (separator < 1) throw new Error('Expected key=value in connection configuration');
      return [line.slice(0, separator).trim(), line.slice(separator + 1).trim()];
    }));
  function resolve(value, chain = []) {
    for (let depth = 0; value.includes('${'); depth++) {
      if (depth > 30) throw new Error('Connection configuration has unresolved or cyclic placeholders');
      const previous = value;
      value = value.replace(/\$\{([^{}]+)\}/g, (_, expression) => {
        const separator = expression.indexOf(':');
        const key = separator < 0 ? expression : expression.slice(0, separator);
        const fallback = separator < 0 ? undefined : expression.slice(separator + 1);
        if (chain.includes(key)) throw new Error('Cyclic connection property: ' + key);
        const replacement = environment[key] ?? properties[key] ?? fallback;
        if (replacement === undefined) throw new Error('Missing connection property: ' + key);
        return resolve(String(replacement), [...chain, key]);
      });
      if (value === previous) throw new Error('Invalid connection placeholder');
    }
    return value;
  }
  return Object.fromEntries(Object.entries(properties).map(([key, value]) => [key, resolve(value)]));
}

function publicConnections(properties) {
  return Object.freeze({
    apiBaseUrl: properties['public.api-base-url'],
    blockchain: Object.freeze({chainId: Number(properties['public.blockchain-chain-id']), explorerBaseUrl: properties['public.blockchain-explorer-base-url']}),
    identity: Object.freeze({
      issuerUri: properties['public.identity-issuer-uri'],
      authorizationUri: properties['public.identity-authorization-uri'],
      tokenUri: properties['public.identity-token-uri'],
      clientId: properties['public.identity-client-id'],
      redirectUri: properties['public.identity-redirect-uri'],
      scopes: properties['public.identity-scopes'],
    }),
  });
}
module.exports = { readConnections, publicConnections };

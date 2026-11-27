'use strict';
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {spawnSync} = require('node:child_process');
const {createHash} = require('node:crypto');
const {Wallet, SigningKey, randomBytes, hexlify, getAddress, keccak256, Interface} = require('ethers');

const ROOT = path.resolve(__dirname, '../..');
const STATE = path.join(ROOT, '.local/besu-network');
const WALLETS = path.join(ROOT, '.local/besu-wallets');
const IMAGE = 'hyperledger/besu:26.9.0';
const CHAIN_ID = 20261125;
const RPC_URL = 'http://127.0.0.1:8547';
const GAS_PRICE = 1000n; // Positive demonstration gas price, in BESU_GAS wei.
const SUPPLY = 1000000n * 1000000n;
const ABI = new Interface([
  'function decimals() view returns(uint8)', 'function symbol() view returns(string)',
  'function totalSupply() view returns(uint256)', 'function balanceOf(address) view returns(uint256)',
  'function transfer(address,uint256) returns(bool)', 'event Transfer(address indexed from,address indexed to,uint256 value)'
]);
function fail(code) { throw new Error(code); }
function address(value) {
  try { const result = getAddress(value); if (BigInt(result) === 0n) fail('ZERO_ADDRESS'); return result.toLowerCase(); }
  catch { fail('INVALID_BESU_ADDRESS'); }
}
function hash(value) {
  if (typeof value !== 'string' || !/^0x[0-9a-fA-F]{64}$/.test(value) || BigInt(value) === 0n) fail('INVALID_BESU_HASH');
  return value.toLowerCase();
}
function quantity(value) {
  if (typeof value !== 'string' || !/^0x[0-9a-fA-F]{1,64}$/.test(value)) fail('INVALID_RPC_QUANTITY');
  return BigInt(value);
}
const json = value => JSON.stringify(value, null, 2) + '\n';
function write(file, value) { fs.writeFileSync(file, typeof value === 'string' ? value : json(value), {flag:'wx', mode:0o600}); }
function read(file) { return JSON.parse(fs.readFileSync(file, 'utf8')); }
function secureFile(file) {
  const stat = fs.lstatSync(file);
  if (!stat.isFile() || stat.isSymbolicLink()) fail('REGULAR_SECRET_FILE_REQUIRED');
  if (process.platform !== 'win32' && ((stat.mode & 0o077) !== 0 || stat.uid !== process.getuid()))
    fail('OWNER_ONLY_SECRET_FILE_REQUIRED');
}
function walletAddress(file) {
  secureFile(file);
  const wallet = read(file), crypto = wallet.crypto || wallet.Crypto;
  if (wallet.version !== 3 || !crypto?.ciphertext || !crypto?.kdf) fail('ENCRYPTED_V3_KEYSTORE_REQUIRED');
  return address('0x' + String(wallet.address).replace(/^0x/i, ''));
}
function docker(args, {env, capture = false} = {}) {
  const result = spawnSync('docker', args, {cwd:ROOT, shell:false, encoding:'utf8',
    env:{...process.env, ...env}, stdio:capture ? 'pipe' : 'inherit', timeout:120000});
  // Never echo captured commands/results: a Docker error can include local paths.
  if (result.error || result.status !== 0) fail('DOCKER_COMMAND_FAILED');
  return capture ? result.stdout.trim() : '';
}
function resourceCheck() {
  const bytes = Number(docker(['info', '--format', '{{.MemTotal}}'], {capture:true}));
  const disk = fs.statfsSync(ROOT);
  const freeDisk = disk.bavail * disk.bsize;
  if (!Number.isFinite(bytes) || bytes < 12 * 1024 ** 3 || freeDisk < 6 * 1024 ** 3)
    fail('BESU_REQUIRES_12_GIB_DOCKER_MEMORY_AND_6_GIB_FREE_DISK');
  return {dockerMemoryBytes:bytes, freeDiskBytes:freeDisk, hostCpuCount:os.availableParallelism()};
}
function genesisConfig(treasury, beneficiary) {
  treasury = address(treasury); beneficiary = address(beneficiary);
  if (treasury === beneficiary) fail('DISTINCT_WALLETS_REQUIRED');
  return {genesis:{
    config:{chainId:CHAIN_ID, londonBlock:0, zeroBaseFee:true,
      qbft:{blockperiodseconds:2, epochlength:30000, requesttimeoutseconds:4}},
    nonce:'0x0', timestamp:'0x0', gasLimit:'0x1c9c380', difficulty:'0x1',
    mixHash:'0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365',
    coinbase:'0x'+'0'.repeat(40),
    alloc:{[treasury.slice(2)]:{balance:'1000000000000000000000'},
      [beneficiary.slice(2)]:{balance:'1000000000000000000000'}}
  }, blockchain:{nodes:{generate:true,count:4}}};
}
function nodeConfig(index) {
  return [
    'data-path="/var/lib/besu"', 'genesis-file="/network/genesis.json"',
    'node-private-key-file="/identity/key"', 'profile="ENTERPRISE"',
    'data-storage-format="FOREST"', 'sync-mode="FULL"',
    'network-id=20261125', 'p2p-host="172.30.76.'+(10+index)+'"', 'p2p-port=30303',
    'discovery-enabled=false', 'bootnodes=[]', 'min-gas-price=1000',
    'permissions-nodes-config-file-enabled=true', 'permissions-accounts-config-file-enabled=true',
    'rpc-http-enabled='+(index===5), ...(index===5 ? [
      'rpc-http-host="0.0.0.0"', 'rpc-http-port=8545',
      'rpc-http-api=["ETH","NET","WEB3","QBFT"]',
      'host-allowlist=["127.0.0.1","localhost","besu-rpc"]', 'rpc-http-cors-origins=[]'
    ] : []), 'rpc-ws-enabled=false', 'graphql-http-enabled=false', 'metrics-enabled=false', ''
  ].join('\n');
}
function init() {
  if (process.platform === 'win32') fail('INITIALIZE_IN_LINUX_CODESPACES_OR_WSL');
  if (fs.existsSync(STATE)) fail('BESU_STATE_EXISTS_DO_NOT_OVERWRITE');
  const treasury = walletAddress(path.join(WALLETS,'treasury-wallet.json'));
  const beneficiary = walletAddress(path.join(WALLETS,'beneficiary-wallet.json'));
  // Refuse reuse of this checkout's public wallet.
  const publicFile = path.join(ROOT,'.local/wallets/treasury-wallet.json');
  if (fs.existsSync(publicFile) && [treasury,beneficiary].includes(walletAddress(publicFile)))
    fail('DEDICATED_BESU_WALLETS_REQUIRED');
  const resources = resourceCheck();
  const local = path.dirname(STATE);
  fs.mkdirSync(local,{recursive:true,mode:0o700});
  if (fs.lstatSync(local).isSymbolicLink()) fail('LOCAL_DIRECTORY_SYMLINK_FORBIDDEN');
  fs.mkdirSync(STATE,{mode:0o700}); // Exclusive; no destructive reset command.
  write(path.join(STATE,'qbft-config.json'),genesisConfig(treasury,beneficiary));
  docker(['run','--rm','--user',process.getuid()+':'+process.getgid(),
    '-v',STATE+':/work',IMAGE,'operator','generate-blockchain-config',
    '--config-file=/work/qbft-config.json','--to=/work/generated','--private-key-file-name=key'],{capture:true});
  const generated = path.join(STATE,'generated');
  const keys = fs.readdirSync(path.join(generated,'keys')).sort();
  if (keys.length !== 4) fail('FOUR_VALIDATORS_REQUIRED');
  fs.copyFileSync(path.join(generated,'genesis.json'),path.join(STATE,'genesis.json'));
  fs.chmodSync(path.join(STATE,'genesis.json'),0o600);
  const enodes=[], validators=[];
  for (let i=1;i<=5;i++) {
    const dir=path.join(STATE,'node-'+i); fs.mkdirSync(dir,{mode:0o700}); fs.mkdirSync(path.join(dir,'data'),{mode:0o700});
    let key;
    if (i<=4) { key=fs.readFileSync(path.join(generated,'keys',keys[i-1],'key'),'utf8').trim(); validators.push(address(keys[i-1])); }
    else key=hexlify(randomBytes(32)).slice(2);
    // Validate scalar and derive public node identity; never print the key.
    const pub=new SigningKey('0x'+key.replace(/^0x/,'')).publicKey.slice(4);
    write(path.join(dir,'key'),key.replace(/^0x/,'')+'\n');
    enodes.push('enode://'+pub+'@172.30.76.'+(10+i)+':30303');
    write(path.join(dir,'node.toml'),nodeConfig(i));
  }
  const permissions='nodes-allowlist='+JSON.stringify(enodes)+'\naccounts-allowlist='+JSON.stringify([treasury,beneficiary])+'\n';
  for(let i=1;i<=5;i++) {
    const dir=path.join(STATE,'node-'+i,'data');
    write(path.join(dir,'static-nodes.json'),enodes);
    write(path.join(dir,'permissions_config.toml'),permissions);
  }
  write(path.join(STATE,'manifest.json'),{
    schemaVersion:1,networkId:'besu-local',chainId:CHAIN_ID,classification:'PRIVATE_DEMO',
    image:IMAGE,evmVersion:'london',feeModel:'EXECUTION_ONLY',gasAsset:'BESU_GAS',
    treasury,beneficiary,validators,genesisExtraData:read(path.join(STATE,'genesis.json')).extraData,genesisFileSha256:createHash('sha256').update(fs.readFileSync(path.join(STATE,'genesis.json'))).digest('hex'),
    resourcesAtInit:resources,initializedAt:new Date().toISOString()
  });
  return {status:'BESU_INITIALIZED',transactionSubmitted:false,chainStarted:false};
}
function manifest() {
  const m=read(path.join(STATE,'manifest.json'));
  if (m.schemaVersion!==1 || m.networkId!=='besu-local' || m.chainId!==CHAIN_ID ||
      m.classification!=='PRIVATE_DEMO' || m.feeModel!=='EXECUTION_ONLY' || m.gasAsset!=='BESU_GAS' || m.image!==IMAGE)
    fail('INVALID_BESU_MANIFEST');
  address(m.treasury); address(m.beneficiary);
  if(read(path.join(STATE,'genesis.json')).extraData!==m.genesisExtraData)fail('GENESIS_VALIDATOR_DATA_CHANGED');
  if(m.genesisHash&&read(path.join(STATE,'identity.json')).genesisHash!==m.genesisHash)fail('PINNED_IDENTITY_RECORD_MISMATCH');
  if(createHash('sha256').update(fs.readFileSync(path.join(STATE,'genesis.json'))).digest('hex')!==m.genesisFileSha256)
    fail('GENESIS_FILE_CHANGED');
  return m;
}
function compose(action) {
  manifest();
  if (process.platform==='win32') fail('RUN_CHAIN_IN_LINUX_CODESPACES_OR_WSL');
  if(action==='start') resourceCheck();
  docker(['compose','--project-name','stablecoins-besu','--project-directory',ROOT,
    '-f',path.join(__dirname,'compose.yml'),...(action==='start'?['up','-d']:action==='stop'?['stop']:['ps'])],
    {env:{BESU_STATE_DIR:STATE,BESU_LOCAL_UID:String(process.getuid()),BESU_LOCAL_GID:String(process.getgid())}});
  return {status:action==='start'?'BESU_START_REQUESTED':action==='stop'?'BESU_STOPPED':'BESU_INVENTORY',transactionSubmitted:false};
}
function rpcClient(fetcher=fetch) {
  let id=0;
  return async (method,params=[])=>{
    const requestId=++id; let response,data;
    try {
      response=await fetcher(RPC_URL,{method:'POST',redirect:'error',signal:AbortSignal.timeout(15000),
        headers:{'Content-Type':'application/json'},body:JSON.stringify({jsonrpc:'2.0',id:requestId,method,params})});
      if(!response.ok)fail('RPC_HTTP_ERROR');
      const body=await response.text();if(body.length>2*1024*1024)fail('RPC_RESPONSE_TOO_LARGE');data=JSON.parse(body);
    }catch{fail('BESU_RPC_UNAVAILABLE_OR_INVALID');}
    if(data?.jsonrpc!=='2.0'||data.id!==requestId||data.error||!Object.hasOwn(data,'result'))fail('BESU_RPC_REJECTED');
    return data.result;
  };
}
async function identity(m,rpc,{enroll=false}={}) {
  if(quantity(await rpc('eth_chainId'))!==BigInt(CHAIN_ID))fail('BESU_WRONG_CHAIN');
  const genesis=await rpc('eth_getBlockByNumber',['0x0',false]);
  const actual=hash(genesis?.hash);
  if(!enroll && actual!==hash(m.genesisHash))fail('BESU_GENESIS_MISMATCH');
  if(typeof m.genesisExtraData!=='string'||String(genesis.extraData).toLowerCase()!==m.genesisExtraData.toLowerCase())fail('BESU_VALIDATOR_GENESIS_MISMATCH');
  const validators=await rpc('qbft_getValidatorsByBlockNumber',['0x0']);
  if(!Array.isArray(validators)||JSON.stringify(validators.map(address).sort())!==JSON.stringify(m.validators.map(address).sort()))
    fail('BESU_VALIDATOR_SET_MISMATCH');
  return actual;
}
async function pin(m,rpc) {
  const actual=await identity(m,rpc,{enroll:true});
  if(m.genesisHash) {
    if(m.genesisHash!==actual)fail('BESU_GENESIS_MISMATCH');
    return {status:'BESU_IDENTITY_ALREADY_PINNED',genesisHash:actual,transactionSubmitted:false};
  }
  // TOFU is an explicit, one-time LOCAL bootstrap step, never execution-time discovery.
  const file=path.join(STATE,'identity.json');
  if(fs.existsSync(file))fail('IDENTITY_RECORD_EXISTS_REVIEW_REQUIRED');
  write(file,{genesisHash:actual,pinnedAt:new Date().toISOString(),method:'LOCAL_BOOTSTRAP_WITH_GENESIS_VALIDATOR_CHECK'});
  fs.writeFileSync(path.join(STATE,'manifest.json'),json({...m,genesisHash:actual}),{mode:0o600});
  return {status:'BESU_IDENTITY_PINNED',genesisHash:actual,transactionSubmitted:false};
}
async function tokenIdentity(m,rpc) {
  await identity(m,rpc);
  if(!m.token)fail('BESU_TOKEN_NOT_DEPLOYED');
  const contract=address(m.token.contract),code=await rpc('eth_getCode',[contract,'latest']);
  if(!/^0x(?:[0-9a-fA-F]{2})+$/.test(code)||keccak256(code)!==hash(m.token.codeHash))fail('BESU_TOKEN_CODE_MISMATCH');
  const call=async name=>ABI.decodeFunctionResult(name,await rpc('eth_call',[{to:contract,data:ABI.encodeFunctionData(name)},'latest']))[0];
  if(await call('decimals')!==6n||await call('symbol')!=='DEMOUSD'||await call('totalSupply')!==SUPPLY)
    fail('BESU_TOKEN_IDENTITY_MISMATCH');
  return contract;
}
function units(value) {
  if(typeof value!=='string'||!/^(0|[1-9][0-9]{0,11})(\.[0-9]{1,6})?$/.test(value))fail('INVALID_DEMOUSD_AMOUNT');
  const [whole,fraction='']=value.split('.');const result=BigInt(whole)*1000000n+BigInt(fraction.padEnd(6,'0'));
  if(result<=0n)fail('INVALID_DEMOUSD_AMOUNT');return result;
}
async function preflight(m,rpc,amount='1',delay=ms=>new Promise(resolve=>setTimeout(resolve,ms))) {
  const contract=await tokenIdentity(m,rpc),requested=units(amount);
  const first=quantity(await rpc('eth_blockNumber'));await delay(3000);
  const latest=quantity(await rpc('eth_blockNumber'));if(latest<=first)fail('BESU_BLOCKS_NOT_PROGRESSING');
  const balance=async who=>quantity(await rpc('eth_call',[{to:contract,data:ABI.encodeFunctionData('balanceOf',[who])},'latest']));
  const token=await balance(m.treasury),beneficiary=await balance(m.beneficiary);
  const native=quantity(await rpc('eth_getBalance',[m.treasury,'latest']));
  const gasPrice=quantity(await rpc('eth_gasPrice'));if(gasPrice<GAS_PRICE)fail('BESU_GAS_POLICY_MISMATCH');
  const gas=quantity(await rpc('eth_estimateGas',[{from:m.treasury,to:contract,gasPrice:'0x'+gasPrice.toString(16),
    data:ABI.encodeFunctionData('transfer',[m.beneficiary,requested])}]));
  const gasLimit=(gas*120n+99n)/100n,required=gasLimit*gasPrice;
  return {status:token>=requested&&native>=required?'PRIVATE_CHAIN_PREFLIGHT_READY':'BESU_FUNDING_REQUIRED',
    network:m.networkId,chainId:CHAIN_ID,classification:'PRIVATE_DEMO',genesisHash:m.genesisHash,tokenContract:contract,
    token:'DEMOUSD',decimals:6,treasury:m.treasury,beneficiary:m.beneficiary,
    treasuryTokenUnits:token.toString(),beneficiaryTokenUnits:beneficiary.toString(),requestedTokenUnits:requested.toString(),
    nativeAsset:'BESU_GAS',nativeBalanceWei:native.toString(),gasLimit:gasLimit.toString(),
    gasPriceWei:gasPrice.toString(),requiredNativeWei:required.toString(),observedBlock:latest.toString(),
    applicationIntegration:'NOT_ENABLED',permissions:'CONFIGURED; NEGATIVE_RUNTIME_TEST_PENDING',transactionSubmitted:false};
}
module.exports={ROOT,STATE,WALLETS,IMAGE,CHAIN_ID,RPC_URL,GAS_PRICE,SUPPLY,ABI,fail,address,hash,quantity,json,write,read,
  secureFile,walletAddress,genesisConfig,nodeConfig,init,manifest,compose,rpcClient,identity,pin,tokenIdentity,units,preflight};

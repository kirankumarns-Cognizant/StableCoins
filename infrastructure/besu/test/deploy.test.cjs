'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {Wallet,keccak256}=require('ethers');
const n=require('../network.cjs');
const compiler=require('../compile.cjs');
const {deploy}=require('../deploy.cjs');

test('durable deployment record prevents another transfer after an uncertain submission',async t=>{
  const temp=fs.mkdtempSync(path.join(os.tmpdir(),'stableflow-besu-deploy-test-'));
  const previous={ROOT:n.ROOT,STATE:n.STATE,WALLETS:n.WALLETS};
  const wallet=Wallet.createRandom();
  const genesisHash='0x'+'a'.repeat(64),extra='0x1234';
  const artifact={abi:['constructor(address treasury)'],bytecode:'0x6001600055',runtimeCodeHash:keccak256('0x6001')};
  let unlocks=0,broadcasts=0;
  try {
    n.ROOT=temp;n.STATE=path.join(temp,'state');n.WALLETS=path.join(temp,'wallets');
    fs.mkdirSync(n.STATE);fs.mkdirSync(n.WALLETS);
    fs.mkdirSync(path.join(temp,'.local/besu-build'),{recursive:true});
    fs.writeFileSync(path.join(temp,'.local/besu-build/BesuDemoUSD.json'),JSON.stringify(artifact));
    fs.writeFileSync(path.join(n.WALLETS,'treasury-wallet.json'),JSON.stringify({
      version:3,address:wallet.address.slice(2),crypto:{ciphertext:'fixture',kdf:'scrypt'}
    }),{mode:0o600});
    const password=path.join(temp,'password');
    fs.writeFileSync(password,'fixture-not-a-real-password',{mode:0o600});
    t.mock.method(compiler,'compile',()=>artifact);
    t.mock.method(Wallet,'fromEncryptedJson',async()=>{unlocks++;return wallet;});
    const m={treasury:wallet.address.toLowerCase(),genesisHash,genesisExtraData:extra,validators:[wallet.address]};
    const rpc=async(method,params=[])=>{
      switch(method) {
        case 'eth_chainId':return '0x'+n.CHAIN_ID.toString(16);
        case 'eth_getBlockByNumber':return {hash:genesisHash,extraData:extra};
        case 'qbft_getValidatorsByBlockNumber':return [wallet.address];
        case 'eth_gasPrice':return '0x3e8';
        case 'eth_estimateGas':return '0x186a0';
        case 'eth_getBalance':return '0xde0b6b3a7640000';
        case 'eth_getTransactionCount':return '0x0';
        case 'eth_sendRawTransaction':
          broadcasts++;
          assert.ok(fs.existsSync(path.join(n.STATE,'deployment.json')),'persist before broadcast');
          throw new Error('BESU_RPC_UNAVAILABLE_OR_INVALID');
        case 'eth_getTransactionReceipt':return null;
        default:throw new Error('UNEXPECTED_TEST_RPC');
      }
    };
    await assert.rejects(deploy(m,rpc,password),/BESU_RPC_UNAVAILABLE_OR_INVALID/);
    const saved=JSON.parse(fs.readFileSync(path.join(n.STATE,'deployment.json'),'utf8'));
    assert.equal(saved.hash,keccak256(saved.signedTransaction));
    const recovered=await deploy(m,rpc,password);
    assert.equal(recovered.status,'DEPLOYMENT_PENDING_OR_UNKNOWN');
    assert.equal(unlocks,1);
    assert.equal(broadcasts,1,'recovery must not submit another deployment');
    // A private chain reset must not poll or replace the saved submission.
    await assert.rejects(deploy({...m,genesisHash:'0x'+'b'.repeat(64)},rpc,password),/BESU_GENESIS_MISMATCH/);
    assert.equal(broadcasts,1);
  } finally {
    Object.assign(n,previous);
    assert.equal(path.dirname(path.resolve(temp)),path.resolve(os.tmpdir()));
    fs.rmSync(temp,{recursive:true,force:true});
  }
});

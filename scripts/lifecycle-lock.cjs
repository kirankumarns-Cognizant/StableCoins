// OS-owned loopback mutex: automatically released when the lifecycle process exits.
const net=require('node:net');
const {createHash}=require('node:crypto');
const {realpathSync}=require('node:fs');
function lockPort(root) {
 const name=realpathSync(root);return 45000+createHash('sha256').update(process.platform==='win32'?name.toLowerCase():name).digest().readUInt32BE(0)%15000;
}
async function acquireLifecycle(root) {
 const server=net.createServer(socket=>socket.destroy());
 await new Promise((resolve,reject)=>{
  server.once('error',()=>reject(Error('Another start, restart or stop command is active, or its local lock port is occupied. Wait for it to finish, then retry.')));
  server.listen({host:'127.0.0.1',port:lockPort(root),exclusive:true},resolve);
 });
 server.unref();let release;
 return ()=>release || (release=new Promise(resolve=>server.close(resolve)));
}
module.exports={lockPort,acquireLifecycle};

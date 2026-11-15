'use strict';
// Opt-in complete step-8 run. Configuration and financial verifiers are reused.
const {run}=require('./run-wallet-flow.cjs');
run({...process.env,DEMO_COMPLETE_LIFECYCLE:'true'}).catch(error=>{console.error(error.message);process.exitCode=1;});

#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { BackupStack, backupTarget } from '../lib/backup-stack';

const target = backupTarget(process.env.BACKUP_ACCOUNT_ID, process.env.BACKUP_REGION);
const app = new cdk.App();
new BackupStack(app, 'StableCoinsRecovery', {
  backupAccount: target.account,
  backupRegion: target.region,
  description: 'Private retained backup storage only; encrypted capture and restore acceptance are separate',
});

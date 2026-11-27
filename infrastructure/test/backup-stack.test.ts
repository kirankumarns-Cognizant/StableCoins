import * as cdk from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import { BackupStack, backupTarget } from '../lib/backup-stack';

function template() {
  return Template.fromStack(new BackupStack(new cdk.App(), 'RecoveryTest', {
    backupAccount: '123456789012',
  }));
}

test('recovery storage is private, encrypted, versioned and ACL-free', () => {
  const t = template();
  t.resourceCountIs('AWS::S3::Bucket', 1);
  t.hasResourceProperties('AWS::S3::Bucket', {
    BucketName: 'stablecoins-recovery-123456789012-us-west-2',
    BucketEncryption: { ServerSideEncryptionConfiguration: [
      { ServerSideEncryptionByDefault: { SSEAlgorithm: 'AES256' } },
    ] },
    VersioningConfiguration: { Status: 'Enabled' },
    OwnershipControls: { Rules: [{ ObjectOwnership: 'BucketOwnerEnforced' }] },
    PublicAccessBlockConfiguration: {
      BlockPublicAcls: true, BlockPublicPolicy: true,
      IgnorePublicAcls: true, RestrictPublicBuckets: true,
    },
    LifecycleConfiguration: Match.absent(),
    ObjectLockEnabled: Match.absent(),
  });
});

test('HTTP is denied for bucket and objects and retained policy survives stack deletion', () => {
  const t = template();
  t.hasResource('AWS::S3::BucketPolicy', {
    DeletionPolicy: 'Retain', UpdateReplacePolicy: 'Retain',
    Properties: { PolicyDocument: { Statement: Match.arrayWith([
      Match.objectLike({ Effect: 'Deny', Action: 's3:*', Principal: { AWS: '*' },
        Condition: { Bool: { 'aws:SecureTransport': 'false' } },
        Resource: [Match.anyValue(), Match.anyValue()],
      }),
    ]) } },
  });
  t.hasResource('AWS::S3::Bucket', {
    DeletionPolicy: 'Retain', UpdateReplacePolicy: 'Retain',
  });
});

test('storage synthesis adds no application, cleanup or IAM resources and needs no bootstrap', () => {
  const body = template().toJSON();
  expect(Object.values(body.Resources).map((r: any) => r.Type).sort())
    .toEqual(['AWS::S3::Bucket', 'AWS::S3::BucketPolicy']);
  expect(body.Parameters).toBeUndefined();
  expect(body.Rules).toBeUndefined();
  expect(body.Outputs.WindowsRepository.Value).toContain('/windows/restic');
  expect(body.Outputs.CodespacesRepository.Value).toContain('/codespaces/restic');
});

test('account must be explicit and Region/environment mistakes fail before synthesis', () => {
  for (const account of [undefined, '', '123', ' 123456789012', '1234567890123']) {
    expect(() => backupTarget(account)).toThrow('BACKUP_ACCOUNT_ID');
  }
  expect(() => backupTarget('123456789012', 'us-east-1')).toThrow('us-west-2');
  expect(() => new BackupStack(new cdk.App(), 'WrongAccount', {
    backupAccount: '123456789012', env: { account: '210987654321' },
  })).toThrow('must match');
  expect(() => new BackupStack(new cdk.App(), 'WrongRegion', {
    backupAccount: '123456789012', env: { region: 'us-east-1' },
  })).toThrow('must match');
});

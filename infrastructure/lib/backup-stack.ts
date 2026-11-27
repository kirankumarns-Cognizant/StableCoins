import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import { Construct } from 'constructs';

/** Separate from the payment deployment; no application or IAM changes. */
export function backupTarget(account: string | undefined, region = 'us-west-2') {
  if (!account || !/^[0-9]{12}$/.test(account)) {
    throw new Error('BACKUP_ACCOUNT_ID must be the 12-digit account verified with STS');
  }
  if (region !== 'us-west-2') {
    throw new Error('Backup storage is configured only for the owner-selected us-west-2 Region');
  }
  return { account, region, bucketName: `stablecoins-recovery-${account}-${region}` };
}

export interface BackupStackProps extends cdk.StackProps {
  backupAccount: string;
  backupRegion?: string;
}

export class BackupStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: BackupStackProps) {
    const target = backupTarget(props.backupAccount, props.backupRegion);
    if ((props.env?.account && props.env.account !== target.account) ||
        (props.env?.region && props.env.region !== target.region)) {
      throw new Error('Backup target and deployment environment must match');
    }
    super(scope, id, {
      ...props,
      env: { account: target.account, region: target.region },
      // This resource-only stack needs no assets, bootstrap bucket or new deployment roles.
      synthesizer: new cdk.BootstraplessSynthesizer(),
    });
    const bucket = new s3.Bucket(this, 'RecoveryBucket', {
      bucketName: target.bucketName,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      objectOwnership: s3.ObjectOwnership.BUCKET_OWNER_ENFORCED,
      encryption: s3.BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      versioned: true,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
      autoDeleteObjects: false,
      // No expiration/transitions: Restic snapshots share data objects of different ages.
    });
    // Keep the TLS policy with the retained bucket if the stack is removed.
    bucket.policy?.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN);
    new cdk.CfnOutput(this, 'BackupBucketName', { value: bucket.bucketName });
    new cdk.CfnOutput(this, 'WindowsRepository', {
      value: `s3:s3.${target.region}.amazonaws.com/${target.bucketName}/windows/restic`,
    });
    new cdk.CfnOutput(this, 'CodespacesRepository', {
      value: `s3:s3.${target.region}.amazonaws.com/${target.bucketName}/codespaces/restic`,
    });
  }
}

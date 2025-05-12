import React, { JSX } from 'react';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';

export default function Home(): JSX.Element {
  return (
    <Layout title="Formation Kafka + Testcontainers" description="Apprenez à tester Kafka avec Java, Maven et Docker.">
      <main className="container margin-vert--xl">
        <div className="text--center">
          <h1>🎓 Formation Kafka pour QE / Développeurs</h1>
          <p>Une formation interne pour apprendre à tester Kafka avec JUnit, Maven et Testcontainers.</p>
          <Link className="button button--primary button--lg" to="/docs/00-intro">
            🚀 Commencer la formation
          </Link>
        </div>
      </main>
    </Layout>
  );
}

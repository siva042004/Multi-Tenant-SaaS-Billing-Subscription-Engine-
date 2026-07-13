import React from "react";
import BillingDashboard from "./components/BillingDashboard";

function App() {
  const tenantId =
    process.env.REACT_APP_DEMO_TENANT_ID ||
    "124a31d8-200c-4928-a2cf-5d3ace689afc";

  return <BillingDashboard tenantId={tenantId} />;
}

export default App;
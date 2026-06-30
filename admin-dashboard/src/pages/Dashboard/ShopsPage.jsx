import React from 'react';
import OnboardingTable from '../components/Shops/OnboardingTable';

const ShopsPage = ({ shops, formatTimestamp }) => {
  return (
    <div>
      <OnboardingTable shops={shops} formatTimestamp={formatTimestamp} />
    </div>
  );
};

export default ShopsPage;

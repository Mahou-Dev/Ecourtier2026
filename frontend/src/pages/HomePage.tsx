import React from 'react';
import { HomeHeroSection } from '../components/home/HomeHeroSection';
import { HomeSearchEngine } from '../components/home/HomeSearchEngine';
import { HomeFeaturedListingsSection } from '../components/home/HomeFeaturedListingsSection';
import { HomeTerritoriesSection } from '../components/home/HomeTerritoriesSection';
import { HomeProcessSection } from '../components/home/HomeProcessSection';
import { HomeTrustSection } from '../components/home/HomeTrustSection';

export const HomePage: React.FC = () => {
  return (
    <div className="home-premium-theme pb-20 space-y-16 sm:space-y-20 lg:space-y-24 overflow-x-clip">
      <div>
        <HomeHeroSection />
        <HomeSearchEngine />
      </div>

      <HomeFeaturedListingsSection />

      <HomeTerritoriesSection />

      <HomeProcessSection />

      <HomeTrustSection />
    </div>
  );
};

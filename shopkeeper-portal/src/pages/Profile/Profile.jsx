import React from 'react';
import { useAuth } from '../../context/AuthContext';
import Layout from '../../components/Layout';
import { User, Phone, MapPin, ShieldCheck, Mail } from 'lucide-react';

const Profile = () => {
  const { shopkeeperData } = useAuth();

  const infoItems = [
    { label: 'Full Name', value: shopkeeperData?.name, icon: User },
    { label: 'Mobile Number', value: shopkeeperData?.phoneNumber, icon: Phone },
    { label: 'Shop ID', value: shopkeeperData?.shopId, icon: ShieldCheck },
    { label: 'Account Status', value: shopkeeperData?.status, icon: ShieldCheck, highlight: true },
  ];

  return (
    <Layout>
      <div className="mb-8">
        <h2 className="text-2xl font-bold text-slate-800">My Profile</h2>
        <p className="text-slate-500">View and manage your account information.</p>
      </div>

      <div className="max-w-2xl">
        <div className="bg-white rounded-3xl shadow-sm border border-slate-100 overflow-hidden">
          <div className="h-32 bg-gradient-to-r from-primary to-primary-light"></div>
          <div className="px-8 pb-8">
            <div className="relative -mt-12 mb-6">
              <div className="w-24 h-24 bg-white rounded-2xl shadow-lg flex items-center justify-center p-1">
                <div className="w-full h-full bg-slate-100 rounded-xl flex items-center justify-center text-slate-400 font-bold text-3xl">
                  {shopkeeperData?.name?.charAt(0) || 'S'}
                </div>
              </div>
            </div>

            <div className="space-y-6">
              <div>
                <h3 className="text-xl font-bold text-slate-800">{shopkeeperData?.name}</h3>
                <p className="text-slate-500 text-sm">Registered Shopkeeper</p>
              </div>

              <div className="grid grid-cols-1 gap-4">
                {infoItems.map((item, i) => (
                  <div key={i} className="flex items-center gap-4 p-4 bg-slate-50 rounded-2xl">
                    <div className="w-10 h-10 bg-white rounded-xl flex items-center justify-center text-slate-400 shadow-sm">
                      <item.icon size={20} />
                    </div>
                    <div>
                      <p className="text-[10px] uppercase tracking-wider text-slate-400 font-bold">{item.label}</p>
                      <p className={`font-semibold ${item.highlight ? 'text-primary' : 'text-slate-700'}`}>
                        {item.value || 'Not provided'}
                      </p>
                    </div>
                  </div>
                ))}
              </div>

              <div className="pt-6 border-t border-slate-100">
                <button className="w-full py-4 bg-slate-800 text-white font-bold rounded-xl hover:bg-slate-900 transition-all">
                  Edit Profile Details
                </button>
                <p className="text-center text-xs text-slate-400 mt-4">
                  To change your registered mobile number, please contact AgroVision support.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default Profile;

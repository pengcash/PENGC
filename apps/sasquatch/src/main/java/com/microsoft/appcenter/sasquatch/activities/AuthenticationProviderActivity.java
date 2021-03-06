/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.appcenter.sasquatch.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.microsoft.appcenter.analytics.AuthenticationProvider;
import com.microsoft.appcenter.sasquatch.R;
import com.microsoft.appcenter.sasquatch.features.TestFeatures;
import com.microsoft.appcenter.sasquatch.features.TestFeaturesListAdapter;
import com.microsoft.appcenter.utils.async.AppCenterConsumer;
import com.microsoft.appcenter.utils.async.AppCenterFuture;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.microsoft.appcenter.sasquatch.SasquatchConstants.ACCOUNT_ID;
import static com.microsoft.appcenter.sasquatch.activities.MainActivity.LOG_TAG;

public class AuthenticationProviderActivity extends AppCompatActivity {

    private boolean mUserLeaving;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_provider_list);

        /* Populate UI. */
        List<TestFeatures.TestFeatureModel> featureList = new ArrayList<>();
        featureList.add(new TestFeatures.TestFeatureTitle(R.string.msa_title));
        featureList.add(new TestFeatures.TestFeature(R.string.msa_compact_title, R.string.msa_compact_description, new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startMSALoginActivity(AuthenticationProvider.Type.MSA_COMPACT);
            }
        }));
        featureList.add(new TestFeatures.TestFeature(R.string.msa_delegate_title, R.string.msa_delegate_description, new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startMSALoginActivity(AuthenticationProvider.Type.MSA_DELEGATE);
            }
        }));

        /* TODO remove reflection once Auth published to jCenter. */
        try {
            final Class<?> auth = Class.forName("com.microsoft.appcenter.auth.Auth");
            featureList.add(new TestFeatures.TestFeature(R.string.b2c_sign_in_title, R.string.b2c_sign_in_description, new View.OnClickListener() {

                /* TODO remove reflection once Auth published to jCenter. Remove this annotation too. */
                @SuppressWarnings("unchecked")
                @Override
                public void onClick(View v) {
                    try {
                        AppCenterFuture<Object> future = (AppCenterFuture<Object>) auth.getMethod("signIn").invoke(null);
                        future.thenAccept(new AppCenterConsumer<Object>() {

                            @Override
                            public void accept(Object signInResult) {
                                try {
                                    Class<?> signInResultClass = signInResult.getClass();
                                    Method getException = signInResultClass.getMethod("getException");
                                    Exception exception = (Exception) getException.invoke(signInResult);
                                    if (exception != null) {
                                        throw exception;
                                    }
                                    Method getUserInformation = signInResultClass.getMethod("getUserInformation");
                                    Object userInformation = getUserInformation.invoke(signInResult);
                                    String accountId = (String) userInformation.getClass().getMethod("getAccountId").invoke(userInformation);
                                    SharedPreferences.Editor edit = MainActivity.sSharedPreferences.edit();
                                    edit.putString("accountId", accountId);
                                    edit.apply();
                                    Log.i(LOG_TAG, "Auth.signIn succeeded, accountId=" + accountId);
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "Auth.signIn failed", e);
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Auth.signIn failed", e);
                    }
                }
            }));
            featureList.add(new TestFeatures.TestFeature(R.string.b2c_sign_out_title, R.string.b2c_sign_out_description, new View.OnClickListener() {

                /* TODO remove reflection once Auth published to jCenter. Remove this annotation too. */
                @SuppressWarnings("unchecked")
                @Override
                public void onClick(View v) {
                    try {
                        auth.getMethod("signOut").invoke(null);
                        SharedPreferences.Editor edit = MainActivity.sSharedPreferences.edit();
                        edit.putString(ACCOUNT_ID, null);
                        edit.apply();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Auth.signOut failed", e);
                    }
                }
            }));
        } catch (ClassNotFoundException ignore) {
        }
        ListView listView = findViewById(R.id.list);
        listView.setAdapter(new TestFeaturesListAdapter(featureList));
        listView.setOnItemClickListener(TestFeatures.getOnItemClickListener());
    }

    private void startMSALoginActivity(AuthenticationProvider.Type type) {
        Intent intent = new Intent(getApplication(), MSALoginActivity.class);
        intent.putExtra(AuthenticationProvider.Type.class.getName(), type);
        startActivity(intent);
    }

    @Override
    protected void onUserLeaveHint() {
        mUserLeaving = true;
    }

    @Override
    protected void onRestart() {

        /* When coming back from browser, finish this intermediate menu screen too. */
        super.onRestart();
        if (mUserLeaving) {
            finish();
        }
    }
}

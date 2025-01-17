/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnucash.android.ui.transaction.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputLayout;

import org.gnucash.android.R;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.PricesDbAdapter;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Price;
import org.gnucash.android.ui.transaction.OnTransferFundsListener;
import org.gnucash.android.ui.transaction.TransactionsActivity;
import org.gnucash.android.util.AmountParser;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Dialog fragment for handling currency conversions when inputting transactions.
 * <p>This is used whenever a multi-currency transaction is being created.</p>
 */
public class TransferFundsDialogFragment extends DialogFragment {

    @BindView(R.id.from_currency)
    TextView mFromCurrencyLabel;
    @BindView(R.id.to_currency)
    TextView mToCurrencyLabel;
    @BindView(R.id.target_currency)
    TextView mConvertedAmountCurrencyLabel;
    @BindView(R.id.amount_to_convert)
    TextView mStartAmountLabel;
    @BindView(R.id.input_exchange_rate)
    EditText mExchangeRateInput;
    @BindView(R.id.input_converted_amount)
    EditText mConvertedAmountInput;
    @BindView(R.id.btn_fetch_exchange_rate)
    Button mFetchExchangeRateButton;
    @BindView(R.id.radio_exchange_rate)
    RadioButton mExchangeRateRadioButton;
    @BindView(R.id.radio_converted_amount)
    RadioButton mConvertedAmountRadioButton;
    @BindView(R.id.label_exchange_rate_example)
    TextView mSampleExchangeRate;
    @BindView(R.id.exchange_rate_text_input_layout)
    TextInputLayout mExchangeRateInputLayout;
    @BindView(R.id.converted_amount_text_input_layout)
    TextInputLayout mConvertedAmountInputLayout;

    @BindView(R.id.btn_save)
    Button mSaveButton;
    @BindView(R.id.btn_cancel)
    Button mCancelButton;
    Money mOriginAmount;
    private Commodity mTargetCommodity;

    Money mConvertedAmount;
    OnTransferFundsListener mOnTransferFundsListener;

    public static TransferFundsDialogFragment getInstance(Money transactionAmount, String targetCurrencyCode,
                                                          OnTransferFundsListener transferFundsListener) {
        TransferFundsDialogFragment fragment = new TransferFundsDialogFragment();
        fragment.mOriginAmount = transactionAmount;
        fragment.mTargetCommodity = CommoditiesDbAdapter.getInstance().getCommodity(targetCurrencyCode);
        fragment.mOnTransferFundsListener = transferFundsListener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_transfer_funds, container, false);
        ButterKnife.bind(this, view);

        TransactionsActivity.displayBalance(mStartAmountLabel, mOriginAmount);
        String fromCurrencyCode = mOriginAmount.getCommodity().getCurrencyCode();
        mFromCurrencyLabel.setText(fromCurrencyCode);
        mToCurrencyLabel.setText(mTargetCommodity.getCurrencyCode());
        mConvertedAmountCurrencyLabel.setText(mTargetCommodity.getCurrencyCode());

        mSampleExchangeRate.setText(String.format(getString(R.string.sample_exchange_rate),
                fromCurrencyCode,
                mTargetCommodity.getCurrencyCode()));
        final InputLayoutErrorClearer textChangeListener = new InputLayoutErrorClearer();

        CommoditiesDbAdapter commoditiesDbAdapter = CommoditiesDbAdapter.getInstance();
        String commodityUID = commoditiesDbAdapter.getCommodityUID(fromCurrencyCode);
        String currencyUID = mTargetCommodity.getUID();
        PricesDbAdapter pricesDbAdapter = PricesDbAdapter.getInstance();
        Pair<Long, Long> pricePair = pricesDbAdapter.getPrice(commodityUID, currencyUID);

        if (pricePair.first > 0 && pricePair.second > 0) {
            // a valid price exists
            Price price = new Price(commodityUID, currencyUID);
            price.setValueNum(pricePair.first);
            price.setValueDenom(pricePair.second);
            mExchangeRateInput.setText(price.toString());

            BigDecimal numerator = new BigDecimal(pricePair.first);
            BigDecimal denominator = new BigDecimal(pricePair.second);
            // convertedAmount = mOriginAmount * numerator / denominator
            BigDecimal convertedAmount = mOriginAmount.asBigDecimal().multiply(numerator)
                    .divide(denominator, mTargetCommodity.getSmallestFractionDigits(), BigDecimal.ROUND_HALF_EVEN);
            DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance();
            mConvertedAmountInput.setText(formatter.format(convertedAmount));
        }

        mExchangeRateInput.addTextChangedListener(textChangeListener);
        mConvertedAmountInput.addTextChangedListener(textChangeListener);

        mConvertedAmountRadioButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mConvertedAmountInput.setEnabled(isChecked);
                mConvertedAmountInputLayout.setErrorEnabled(isChecked);
                mExchangeRateRadioButton.setChecked(!isChecked);
                if (isChecked) {
                    mConvertedAmountInput.requestFocus();
                }
            }
        });

        mExchangeRateRadioButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mExchangeRateInput.setEnabled(isChecked);
                mExchangeRateInputLayout.setErrorEnabled(isChecked);
                mFetchExchangeRateButton.setEnabled(isChecked);
                mConvertedAmountRadioButton.setChecked(!isChecked);
                if (isChecked) {
                    mExchangeRateInput.requestFocus();
                }
            }
        });

        mFetchExchangeRateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO: Pull the exchange rate for the currency here
            }
        });

        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                transferFunds();
            }
        });
        return view;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setTitle(R.string.title_transfer_funds);
        return dialog;
    }

    /**
     * Converts the currency amount with the given exchange rate and saves the price to the db
     */
    private void transferFunds() {
        Price price = null;

        String originCommodityUID = mOriginAmount.getCommodity().getUID();
        String targetCommodityUID = mTargetCommodity.getUID();

        if (mExchangeRateRadioButton.isChecked()) {
            BigDecimal rate;
            try {
                rate = AmountParser.parse(mExchangeRateInput.getText().toString());
            } catch (ParseException e) {
                mExchangeRateInputLayout.setError(getString(R.string.error_invalid_exchange_rate));
                return;
            }
            price = new Price(originCommodityUID, targetCommodityUID, rate);

            mConvertedAmount = mOriginAmount.multiply(rate).withCurrency(mTargetCommodity);
        }

        if (mConvertedAmountRadioButton.isChecked()) {
            BigDecimal amount;
            try {
                amount = AmountParser.parse(mConvertedAmountInput.getText().toString());
            } catch (ParseException e) {
                mConvertedAmountInputLayout.setError(getString(R.string.error_invalid_amount));
                return;
            }
            mConvertedAmount = new Money(amount, mTargetCommodity);

            price = new Price(originCommodityUID, targetCommodityUID);
            // fractions cannot be exactly represented by BigDecimal.
            price.setValueNum(mConvertedAmount.getNumerator() * mOriginAmount.getDenominator());
            price.setValueDenom(mOriginAmount.getNumerator() * mConvertedAmount.getDenominator());
        }

        price.setSource(Price.SOURCE_USER);
        PricesDbAdapter.getInstance().addRecord(price);

        if (mOnTransferFundsListener != null)
            mOnTransferFundsListener.transferComplete(mConvertedAmount);

        dismiss();
    }

    /**
     * Hides the error message from mConvertedAmountInputLayout and mExchangeRateInputLayout
     * when the user edits their content.
     */
    private class InputLayoutErrorClearer implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            mConvertedAmountInputLayout.setErrorEnabled(false);
            mExchangeRateInputLayout.setErrorEnabled(false);
        }
    }
}

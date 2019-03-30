package me.dmdev.rxpm.sample.main.ui.phone

import com.google.i18n.phonenumbers.NumberParseException
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import me.dmdev.rxpm.bindProgress
import me.dmdev.rxpm.sample.R
import me.dmdev.rxpm.sample.main.ChooseCountryMessage
import me.dmdev.rxpm.sample.main.PhoneSentSuccessfullyMessage
import me.dmdev.rxpm.sample.main.model.AuthModel
import me.dmdev.rxpm.sample.main.ui.base.ScreenPresentationModel
import me.dmdev.rxpm.sample.main.util.Country
import me.dmdev.rxpm.sample.main.util.PhoneUtil
import me.dmdev.rxpm.sample.main.util.ResourceProvider
import me.dmdev.rxpm.sample.main.util.onlyDigits
import me.dmdev.rxpm.skipWhileInProgress
import me.dmdev.rxpm.widget.inputControl


class AuthByPhonePm(
    private val phoneUtil: PhoneUtil,
    private val resourceProvider: ResourceProvider,
    private val authModel: AuthModel
) : ScreenPresentationModel() {

    val chosenCountry = State<Country>()
    val phoneNumber = inputControl(formatter = null)
    val countryCode = inputControl(
        initialText = "+7",
        formatter = {
            val code = "+${it.onlyDigits().take(5)}"
            if (code.length > 5) {
                try {
                    val number = phoneUtil.parsePhone(code)
                    phoneNumberFocus.consumer.accept(Unit)
                    phoneNumber.textChanges.consumer.accept(number.nationalNumber.toString())
                    "+${number.countryCode}"
                } catch (e: NumberParseException) {
                    code
                }
            } else {
                code
            }
        }
    )

    val inProgress = State(false)
    val sendButtonEnabled = State(false)
    val phoneNumberFocus = Command<Unit>(bufferSize = 1)

    val sendAction = Action<Unit>()
    val countryClicks = Action<Unit>()
    val chooseCountryAction = Action<Country>()

    override fun onCreate() {
        super.onCreate()

        countryCode.text.observable
            .map {
                val code = it.onlyDigits()
                if (code.isNotEmpty()) {
                    phoneUtil.getCountryForCountryCode(code.onlyDigits().toInt())
                } else {
                    Country.UNKNOWN
                }
            }
            .subscribe(chosenCountry.consumer)
            .untilDestroy()

        Observable.combineLatest(phoneNumber.textChanges.observable, chosenCountry.observable,
            BiFunction { number: String, country: Country ->
                phoneUtil.formatPhoneNumber(country, number)
            })
            .subscribe(phoneNumber.text.consumer)
            .untilDestroy()


        Observable.combineLatest(phoneNumber.textChanges.observable, chosenCountry.observable,
            BiFunction { number: String, country: Country ->
                phoneUtil.isValidPhone(country, number)
            })
            .subscribe(sendButtonEnabled.consumer)
            .untilDestroy()

        countryClicks.observable
            .subscribe {
                sendMessage(ChooseCountryMessage())
            }
            .untilDestroy()

        chooseCountryAction.observable
            .subscribe {
                countryCode.textChanges.consumer.accept("+${it.countryCallingCode}")
                chosenCountry.consumer.accept(it)
                phoneNumberFocus.consumer.accept(Unit)
            }
            .untilDestroy()

        sendAction.observable
            .skipWhileInProgress(inProgress.observable)
            .filter { validateForm() }
            .map { "${countryCode.text.value} ${phoneNumber.text.value}" }
            .switchMapCompletable { phone ->
                authModel.sendPhone(phone)
                    .bindProgress(inProgress.consumer)
                    .doOnComplete {
                        sendMessage(PhoneSentSuccessfullyMessage(phone))
                    }
                    .doOnError { showError(it.message) }
            }
            .retry()
            .subscribe()
            .untilDestroy()
    }

    private fun validateForm(): Boolean {

        return if (phoneNumber.text.value.isEmpty()) {
            phoneNumber.error.consumer.accept(resourceProvider.getString(R.string.enter_phone_number))
            false
        } else if (!phoneUtil.isValidPhone(chosenCountry.value, phoneNumber.text.value)) {
            phoneNumber.error.consumer.accept(resourceProvider.getString(R.string.invalid_phone_number))
            false
        } else {
            true
        }

    }

}
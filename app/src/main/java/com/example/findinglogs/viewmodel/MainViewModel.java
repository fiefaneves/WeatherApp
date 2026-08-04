package com.example.findinglogs.viewmodel;


import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.findinglogs.model.model.Weather;
import com.example.findinglogs.model.repo.Repository;
import com.example.findinglogs.model.repo.remote.api.WeatherCallback;
import com.example.findinglogs.model.util.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainViewModel extends AndroidViewModel {

    private static final String TAG = MainViewModel.class.getSimpleName();
    private static final int FETCH_INTERVAL = 120_000;
    private final Repository mRepository;
    private final MutableLiveData<List<Weather>> _weatherList = new MutableLiveData<>(new ArrayList<>());
    private final LiveData<List<Weather>> weatherList = _weatherList;

    private int currentSessionId = 0;

    private final Handler handler;
    private final Runnable fetchRunnable = this::fetchAllForecasts;

    public MainViewModel(Application application) {
        this(application, new Repository(application), new Handler(Looper.getMainLooper()));
    }

    // Package-private constructor for testing
    MainViewModel(Application application, Repository repository, Handler handler) {
        super(application);
        this.mRepository = repository;
        this.handler = handler;
        startFetching();
    }

    public LiveData<List<Weather>> getWeatherList() {
        return weatherList;
    }

    private void startFetching() {
        fetchAllForecasts();
    }

    public void refreshData() {
        if (Logger.ISLOGABLE) Logger.d(TAG, "refreshData()");
        handler.removeCallbacks(fetchRunnable);
        fetchAllForecasts();
    }

    private void fetchAllForecasts() {
        if (Logger.ISLOGABLE) Logger.d(TAG, "fetchAllForecasts()");
        currentSessionId++;
        final int sessionId = currentSessionId;

        final Map<String, String> localizations = mRepository.getLocalizations();
        final int totalLocalizations = localizations.size();
        final int[] finishedCount = {0};
        final Map<String, Weather> weatherResults = new HashMap<>();

        for (Map.Entry<String, String> entry : localizations.entrySet()) {
            final String key = entry.getKey();
            String latlon = entry.getValue();

            mRepository.retrieveForecast(latlon, new WeatherCallback() {
                @Override
                public void onSuccess(Weather result) {
                    synchronized (weatherResults) {
                        weatherResults.put(key, result);
                    }
                    checkCompletion();
                }

                @Override
                public void onFailure(String error) {
                    if (Logger.ISLOGABLE) Logger.w(TAG, "onFailure: " + error);
                    checkCompletion();
                }

                private void checkCompletion() {
                    if (sessionId != currentSessionId) {
                        return;
                    }
                    finishedCount[0]++;
                    if (finishedCount[0] == totalLocalizations) {
                        List<Weather> orderedList = new ArrayList<>();
                        List<String> cityNames = new ArrayList<>();

                        for (String k : localizations.keySet()) {
                            Weather w = weatherResults.get(k);
                            if (w != null && w.getName() != null && !cityNames.contains(w.getName())) {
                                orderedList.add(w);
                                cityNames.add(w.getName());
                            }
                        }
                        _weatherList.setValue(orderedList);
                        handler.postDelayed(fetchRunnable, FETCH_INTERVAL);
                    }
                }
            });
        }
    }

    @Override
    protected void onCleared() {
        handler.removeCallbacks(fetchRunnable);
        super.onCleared();
    }

    public void retrieveForecast(String latLon, WeatherCallback callback) {
        mRepository.retrieveForecast(latLon, callback);
    }
}
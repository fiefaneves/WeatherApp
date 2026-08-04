package com.example.findinglogs.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.os.Handler;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;

import com.example.findinglogs.model.model.Weather;
import com.example.findinglogs.model.repo.Repository;
import com.example.findinglogs.model.repo.remote.api.WeatherCallback;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.List;

public class MainViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private Application application;
    private Repository repository;
    private Handler handler;
    private MainViewModel viewModel;

    @Before
    public void setUp() {
        application = mock(Application.class);
        repository = mock(Repository.class);
        handler = mock(Handler.class);

        HashMap<String, String> localizations = new HashMap<>();
        localizations.put("1", "lat1,lon1");
        localizations.put("2", "lat2,lon2");
        when(repository.getLocalizations()).thenReturn(localizations);
    }

    @Test
    public void testFetchAllForecasts_SuccessPartial() {
        doAnswer(invocation -> {
            WeatherCallback callback = invocation.getArgument(1);
            Weather weather = new Weather();
            weather.setName("City 1");
            callback.onSuccess(weather);
            return null;
        }).when(repository).retrieveForecast(eq("lat1,lon1"), any(WeatherCallback.class));

        doAnswer(invocation -> {
            WeatherCallback callback = invocation.getArgument(1);
            callback.onFailure("Network Error");
            return null;
        }).when(repository).retrieveForecast(eq("lat2,lon2"), any(WeatherCallback.class));

        viewModel = new MainViewModel(application, repository, handler);

        List<Weather> result = viewModel.getWeatherList().getValue();
        assertEquals(1, result.size());
        assertEquals("City 1", result.get(0).getName());

        verify(handler).postDelayed(any(Runnable.class), any(Long.class));
    }

    @Test
    public void testFetchAllForecasts_Deduplication() {
        doAnswer(invocation -> {
            WeatherCallback callback = invocation.getArgument(1);
            Weather weather = new Weather();
            weather.setName("Same City");
            callback.onSuccess(weather);
            return null;
        }).when(repository).retrieveForecast(any(String.class), any(WeatherCallback.class));

        viewModel = new MainViewModel(application, repository, handler);

        List<Weather> result = viewModel.getWeatherList().getValue();
        assertEquals(1, result.size());
        assertEquals("Same City", result.get(0).getName());
    }

    @Test
    public void testFetchAllForecasts_StrictOrder() {
        final WeatherCallback[] callbacks = new WeatherCallback[2];
        doAnswer(invocation -> {
            callbacks[0] = invocation.getArgument(1);
            return null;
        }).when(repository).retrieveForecast(eq("lat1,lon1"), any(WeatherCallback.class));

        doAnswer(invocation -> {
            callbacks[1] = invocation.getArgument(1);
            return null;
        }).when(repository).retrieveForecast(eq("lat2,lon2"), any(WeatherCallback.class));

        viewModel = new MainViewModel(application, repository, handler);

        Weather weather2 = new Weather();
        weather2.setName("City 2");
        callbacks[1].onSuccess(weather2);

        Weather weather1 = new Weather();
        weather1.setName("City 1");
        callbacks[0].onSuccess(weather1);

        List<Weather> result = viewModel.getWeatherList().getValue();
        assertEquals(2, result.size());
        assertEquals("City 1", result.get(0).getName());
        assertEquals("City 2", result.get(1).getName());
    }

    @Test
    public void testFetchAllForecasts_RaceCondition() {
        ArgumentCaptor<WeatherCallback> callbackCaptor = ArgumentCaptor.forClass(WeatherCallback.class);

        viewModel = new MainViewModel(application, repository, handler);

        viewModel.refreshData();

        verify(repository, times(4)).retrieveForecast(any(String.class), callbackCaptor.capture());
        List<WeatherCallback> allCallbacks = callbackCaptor.getAllValues();

        Weather weatherOld = new Weather();
        weatherOld.setName("Old City");
        allCallbacks.get(0).onSuccess(weatherOld);
        allCallbacks.get(1).onSuccess(weatherOld);

        assertEquals(0, viewModel.getWeatherList().getValue().size());

        Weather weatherNew = new Weather();
        weatherNew.setName("New City");
        allCallbacks.get(2).onSuccess(weatherNew);
        allCallbacks.get(3).onSuccess(weatherNew);

        assertEquals(1, viewModel.getWeatherList().getValue().size());
        assertEquals("New City", viewModel.getWeatherList().getValue().get(0).getName());
    }
}

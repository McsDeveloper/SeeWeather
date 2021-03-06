package com.xiecc.seeWeather.modules.main.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import butterknife.Bind;
import butterknife.ButterKnife;
import com.litesuits.orm.db.assit.WhereBuilder;
import com.trello.rxlifecycle.android.FragmentEvent;
import com.xiecc.seeWeather.R;
import com.xiecc.seeWeather.base.BaseFragment;
import com.xiecc.seeWeather.base.C;
import com.xiecc.seeWeather.common.utils.RxUtils;
import com.xiecc.seeWeather.common.utils.SimpleSubscriber;
import com.xiecc.seeWeather.common.utils.Util;
import com.xiecc.seeWeather.component.OrmLite;
import com.xiecc.seeWeather.component.RetrofitSingleton;
import com.xiecc.seeWeather.component.RxBus;
import com.xiecc.seeWeather.modules.main.adapter.MultiCityAdapter;
import com.xiecc.seeWeather.modules.main.domain.CityORM;
import com.xiecc.seeWeather.modules.main.domain.MultiUpdate;
import com.xiecc.seeWeather.modules.main.domain.Weather;
import java.util.ArrayList;
import java.util.List;
import rx.Observable;
import rx.Observer;

/**
 * Created by HugoXie on 16/7/9.
 *
 * Email: Hugo3641@gamil.com
 * GitHub: https://github.com/xcc3641
 * Info:
 */
public class MultiCityFragment extends BaseFragment {

    @Bind(R.id.recyclerview)
    RecyclerView mRecyclerView;
    @Bind(R.id.swiprefresh)
    SwipeRefreshLayout mRefreshLayout;
    @Bind(R.id.empty)
    LinearLayout mLayout;

    private MultiCityAdapter mAdapter;
    private List<Weather> mWeathers;

    private View view;

    /**
     * 加载数据操作,在视图创建之前初始化
     */
    @Override
    protected void lazyLoad() {

    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (view == null) {
            view = inflater.inflate(R.layout.fragment_multicity, container, false);
            ButterKnife.bind(this, view);
        }
        return view;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RxBus.getDefault().toObservable(MultiUpdate.class).subscribe(new SimpleSubscriber<MultiUpdate>() {
            @Override
            public void onNext(MultiUpdate multiUpdate) {
                multiLoad();
            }
        });
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView();
        multiLoad();
    }

    private void initView() {
        mWeathers = new ArrayList<>();
        mAdapter = new MultiCityAdapter(mWeathers);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.setAdapter(mAdapter);
        mAdapter.setOnMultiCityLongClick(new MultiCityAdapter.onMultiCityLongClick() {
            @Override
            public void longClick(String city) {
                new AlertDialog.Builder(getActivity()).setMessage("是否删除该城市?")
                    .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            OrmLite.getInstance().delete(new WhereBuilder(CityORM.class).where("name=?", city));
                            OrmLite.OrmTest(CityORM.class);
                            multiLoad();
                            Snackbar.make(getView(), "已经将" + city + "删掉了 Ծ‸ Ծ", Snackbar.LENGTH_LONG).setAction("撤销",
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        OrmLite.getInstance().save(new CityORM(city));
                                        multiLoad();
                                    }
                                }).show();
                        }
                    })
                    .show();
            }
        });

        if (mRefreshLayout != null) {
            mRefreshLayout.setColorSchemeResources(
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light,
                android.R.color.holo_green_light,
                android.R.color.holo_blue_bright
            );
            mRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    mRefreshLayout.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            multiLoad();
                        }
                    }, 1000);
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
    }

    private void multiLoad() {
        mWeathers.clear();
        Observable.defer(() -> Observable.from(OrmLite.getInstance().query(CityORM.class)))//defer订阅后再执行
            .doOnRequest(aLong -> mRefreshLayout.setRefreshing(true))//请求时，设置SwipeRefreshLayout刷新
            .map(cityORM -> Util.replaceCity(cityORM.getName()))//CityORM.class转String
            .distinct()//取消重复的数据流，发送不重复的
            .flatMap(s -> RetrofitSingleton.getInstance()
                .getApiService()
                .mWeatherAPI(s, C.KEY)
                .map(weatherAPI -> weatherAPI.mHeWeatherDataService30s.get(0))
                .compose(RxUtils.rxSchedulerHelper()))
            .compose(this.bindUntilEvent(FragmentEvent.DESTROY_VIEW))//复合操作
            .filter(weather -> !C.UNKNOWN_CITY.equals(weather.status))//过滤掉UNKNOWN_CITY
            .take(3)//take，只取数据流中的前面3项
            .doOnTerminate(() -> {
                mRefreshLayout.setRefreshing(false);
            })//当onCompleted或者onError时，这个会回调，此时停止SwipeRefreshLayout刷新progress
            .subscribe(new Observer<Weather>() {
                @Override
                public void onCompleted() {
                    mAdapter.notifyDataSetChanged();
                    if (mAdapter.isEmpty()) {
                        mLayout.setVisibility(View.VISIBLE);
                    } else {
                        mLayout.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onError(Throwable e) {
                    if (mAdapter.isEmpty() && mLayout != null) {
                        mLayout.setVisibility(View.VISIBLE);
                    }
                    RetrofitSingleton.disposeFailureInfo(e);
                }

                @Override
                public void onNext(Weather weather) {
                    mWeathers.add(weather);
                }
            });
    }
}

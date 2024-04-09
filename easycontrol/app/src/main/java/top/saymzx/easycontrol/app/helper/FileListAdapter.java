package top.saymzx.easycontrol.app.helper;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.List;
import java.util.Objects;

import top.saymzx.easycontrol.app.R;
import top.saymzx.easycontrol.app.databinding.ItemFileBinding;
import top.saymzx.easycontrol.app.entity.MyInterface;

public class FileListAdapter extends BaseAdapter {

  private final Context context;
  private final MyInterface.MyFunction onSelect;
  private final List<String> listFiles;

  public String localSelect = "";

  public FileListAdapter(Context c, List<String> listFiles, MyInterface.MyFunction onSelect) {
    this.context = c;
    this.onSelect = onSelect;
    this.listFiles = listFiles;
  }

  @Override
  public int getCount() {
    return listFiles.size() + 1;
  }

  @Override
  public Object getItem(int i) {
    return null;
  }

  @Override
  public long getItemId(int i) {
    return 0;
  }

  @Override
  public View getView(int i, View view, ViewGroup viewGroup) {
    if (view == null) {
      ItemFileBinding itemFileBinding = ItemFileBinding.inflate(LayoutInflater.from(context));
      view = itemFileBinding.getRoot();
      view.setTag(itemFileBinding);
    }
    String name = i == 0 ? ".." : listFiles.get(i - 1);
    ItemFileBinding itemFileBinding = (ItemFileBinding) view.getTag();
    itemFileBinding.text.setText(name);
    itemFileBinding.getRoot().setBackgroundResource(Objects.equals(name, localSelect) ? R.drawable.background_cron_small_stroke : R.drawable.background_cron_small);
    itemFileBinding.getRoot().setOnClickListener(v -> {
      localSelect = name;
      update();
      onSelect.run();
    });
    return view;
  }

  public void update() {
    notifyDataSetChanged();
  }

}

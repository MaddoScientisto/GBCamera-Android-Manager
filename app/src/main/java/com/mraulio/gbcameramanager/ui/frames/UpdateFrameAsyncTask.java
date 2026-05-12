package com.mraulio.gbcameramanager.ui.frames;

import com.mraulio.gbcameramanager.utils.AppTask;

import com.mraulio.gbcameramanager.db.FrameDao;
import com.mraulio.gbcameramanager.model.GbcFrame;
import com.mraulio.gbcameramanager.utils.StaticValues;

public class UpdateFrameAsyncTask extends AppTask<Void, Void, Void> {

    private GbcFrame gbcFrame;

    //Stores the image passes as parameter in the constructor
    public UpdateFrameAsyncTask(GbcFrame gbcFrame) {
        this.gbcFrame = gbcFrame;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        FrameDao frameDao = StaticValues.db.frameDao();
        frameDao.update(gbcFrame);
        return null;
    }
}

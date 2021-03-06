/*
 * Copyright (c) 2013 Extradea LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.extradea.framework.images.tasks;

import android.graphics.Bitmap;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 31.07.12
 * Time: 1:48
 */
public abstract class CustomImageTask extends ImageTask {
    public class CustomImageTaskResult {

        private Bitmap result;
        private byte[] binaryResult;

        public Bitmap getResult() {
            return result;
        }

        public void setResult(Bitmap result) {
            this.result = result;
        }

        public byte[] getBinaryResult() {
            return binaryResult;
        }

        public void setBinaryResult(byte[] binaryResult) {
            this.binaryResult = binaryResult;
        }
    }

    public abstract CustomImageTaskResult process();
}

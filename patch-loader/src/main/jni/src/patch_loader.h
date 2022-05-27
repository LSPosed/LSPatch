/*
 * This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2022 LSPosed Contributors
 */

//
// Created by Nullptr on 2022/3/17.
//

#pragma once

#include "context.h"

namespace lspd {

    inline lsplant::InitInfo handler;

    class PatchLoader : public Context {
    public:
        inline static void Init() {
            instance_ = std::make_unique<PatchLoader>();
        }

        inline static PatchLoader* GetInstance() {
            return static_cast<PatchLoader*>(instance_.get());
        }

        void Load(JNIEnv* env);

    protected:
        void InitArtHooker(JNIEnv* env, const lsplant::InitInfo& initInfo) override;

        void InitHooks(JNIEnv* env) override;

        void LoadDex(JNIEnv* env, PreloadedDex&& dex) override;

        void SetupEntryClass(JNIEnv* env) override;
    };
} // namespace lspd

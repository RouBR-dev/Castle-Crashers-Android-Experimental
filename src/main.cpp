// Castle Crashers - ReXGlue Recompiled Project (esqueleto)
// Espelha src/main.cpp do Skate3-Mobile, sem os hooks especificos do Skate 3.
#include "generated/castle_init.h"

#include <memory>
#include <rex/rex_app.h>

class CastleApp : public rex::ReXApp {
 public:
  using rex::ReXApp::ReXApp;
  static std::unique_ptr<rex::ui::WindowedApp> Create(
      rex::ui::WindowedAppContext& ctx) {
    return std::unique_ptr<CastleApp>(
        new CastleApp(ctx, "castle", castle_PPCImageConfig));
  }
};

REX_DEFINE_APP(castle, CastleApp::Create)

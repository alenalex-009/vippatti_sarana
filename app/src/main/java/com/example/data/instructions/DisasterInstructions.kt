package com.example.data.instructions

/**
 * ============================================================================
 * HIERARCHICAL INSTRUCTION SYSTEM — Disaster Category -> Before / During / After
 * ============================================================================
 * Architecture: static English COPY lives here as the single source of truth
 * for instruction CONTENT (titles + details + critical flags). Language
 * switching is handled by Android string resources for every UI label; the
 * guide copy above is verified general public-safety guidance consistent with
 * NDMA/NDRF/IMD public dos-and-don'ts (monitor official warnings, evacuate
 * on orders, never enter floodwater, DROP-COVER-HOLD ON, stay clear of
 * slopes/debris, stay low under smoke, never use lifts in fire, never
 * re-enter until cleared). Nothing here replaces official orders.
 *
 * Content update path (instructions-tab only):
 *  1. Edit/append an InstructionItem below (BEFORE / DURING / AFTER).
 *  2. Keep each detail to 1-2 short sentences, scannable under stress.
 *  3. Critical life-safety items get isCritical = true (red escalation).
 *  4. The disaster's approved safety poster (res/drawable-nodpi/
 *     instructions_poster_*.png, sourced from InstructionImages) is shown by
 *     DisasterInstructionPoster on the disaster screen — no per-item wiring
 *     is needed; new items appear with the existing numbered-step card
 *     style automatically.
 *
 * Protected: navigation (InstructionsRoutes), screens, ViewModel, emergency
 * numbers (112/101/100/108/1077 module) and reporting entry points are
 * untouched by content edits.
 */

/** One instruction item inside a phase. */
data class InstructionItem(
  val title: String,
  val detail: String,
  /** Optional regional/agency context, e.g. "Kerala State advice". */
  val region: String? = null,
  /** CRITICAL items are visually escalated. */
  val isCritical: Boolean = false
)

/** One phase (Before / During / After) of a disaster category. */
data class InstructionPhase(
  val title: String,
  val items: List<InstructionItem>
)

/** One disaster category with its three phases. */
data class DisasterCategory(
  val id: String,
  val title: String,
  val subtitle: String,
  val before: InstructionPhase,
  val during: InstructionPhase,
  val after: InstructionPhase
)

/** Common (non-disaster-specific) guidance module. */
data class CommonModule(
  val id: String,
  val title: String,
  val subtitle: String,
  val items: List<InstructionItem>
)

/** Root of the instruction hierarchy. */
object DisasterInstructions {

  val categories: List<DisasterCategory> = listOf(
    DisasterCategory(
      id = "flood",
      title = "Flood",
      subtitle = "Periyar valley & low-lying settlement guidance",
      before = InstructionPhase(
        title = "Before",
        items = listOf(
          InstructionItem(
            "Know your evacuation route",
            "Identify the nearest safe zone and the road that stays above flood level. Plan two ways out of your settlement.",
            isCritical = true
          ),
          InstructionItem(
            "Keep documents waterproof",
            "ID, land records, ration card and medical prescriptions in a sealed pouch inside your go-bag."
          ),
          InstructionItem(
            "Watch official warnings",
            "Follow KSDMA and district control-room alerts. Do not rely on rumors."
          ),
          InstructionItem(
            "Prepare an emergency kit",
            "Water (2L/person/day), dry rations for 3 days, torch, power bank, whistle and first-aid kit."
          ),
          InstructionItem(
            "Elevate valuables",
            "Move grain, electronics and livestock feed to upper floors or raised platforms before the monsoon peaks."
          ),
          InstructionItem(
            "Know your home mains",
            "Everyone at home should know where the main power switch and LPG regulator are, and how to switch them off quickly."
          )
        )
      ),
      during = InstructionPhase(
        title = "During",
        items = listOf(
          InstructionItem(
            "Never walk or drive through moving water",
            "15 cm of fast water can knock you down; 60 cm can sweep a vehicle away. Turn around and find another way.",
            isCritical = true
          ),
          InstructionItem(
            "Move to higher ground immediately",
            "Do not wait for the water to enter the house. Follow the evacuation route to the marked safe zone.",
            isCritical = true
          ),
          InstructionItem(
            "Cut power at the main switch",
            "If water is entering the building, switch off electricity at the board before leaving."
          ),
          InstructionItem(
            "Do not touch electrical equipment when wet",
            "Standing water plus live wires is lethal."
          ),
          InstructionItem(
            "Help children, elderly and disabled neighbors first",
            "Carry the go-bag; leave heavy furniture behind."
          ),
          InstructionItem(
            "Follow official evacuation orders",
            "When authorities announce evacuation, leave at once. Late evacuation gets caught in rising water."
          ),
          InstructionItem(
            "Stay clear of fallen power lines",
            "Never touch or step over wires in or near water. Warn others from a safe distance."
          ),
          InstructionItem(
            "Keep away from drains, canals and weak structures",
            "Open manholes hide under brown water; walls and footbridges can give way without warning."
          )
        )
      ),
      after = InstructionPhase(
        title = "After",
        items = listOf(
          InstructionItem(
            "Wait for the all-clear",
            "Return only when authorities confirm the water has receded and roads are safe.",
            isCritical = true
          ),
          InstructionItem(
            "Boil or treat all drinking water",
            "Flood water contaminates wells. Use chlorine tablets or boil for 10 minutes."
          ),
          InstructionItem(
            "Avoid contact with flood water",
            "Wear boots; flood water carries sewage, chemicals and snake risk."
          ),
          InstructionItem(
            "Check structural damage before entering",
            "Look for cracked walls, undermined foundations and gas smells."
          ),
          InstructionItem(
            "Report damage for relief",
            "Register losses with the village officer / taluk office so relief and compensation can be processed."
          ),
          InstructionItem(
            "Get wet wiring inspected",
            "Have an electrician check damp switchboards and wiring before switching power back on."
          ),
          InstructionItem(
            "Report blocked or flooded roads",
            "Use the incident report on the Radar tab so neighbours and rescue teams can see the hazard."
          )
        )
      )
    ),
    DisasterCategory(
      id = "landslide",
      title = "Landslide",
      subtitle = "Western Ghats slope & highland guidance",
      before = InstructionPhase(
        title = "Before",
        items = listOf(
          InstructionItem(
            "Learn slope warning signs",
            "New cracks in walls or ground, doors jamming, tilting trees and fences — all signal slow ground movement.",
            isCritical = true
          ),
          InstructionItem(
            "Identify a safe assembly point",
            "Choose open, flat ground away from the slope base; never downhill of a cut slope."
          ),
          InstructionItem(
            "Keep drainage clear",
            "Unblock household and hill drains — clogged water is the main landslide trigger in Kerala's highlands.",
            region = "Kerala highlands"
          ),
          InstructionItem(
            "Avoid building at slope base",
            "Consult the district geologist before new construction on or below steep slopes."
          ),
          InstructionItem(
            "Track official rain and landslide warnings",
            "Follow IMD district bulletins and control-room alerts through heavy rain; act on warnings, not rumours."
          ),
          InstructionItem(
            "Keep the emergency kit reachable",
            "Water, medicines, torch and documents in one bag you can grab in seconds."
          )
        )
      ),
      during = InstructionPhase(
        title = "During",
        items = listOf(
          InstructionItem(
            "Evacuate at the first sign of movement",
            "Move away from the slope immediately — sideways out of the slide path, not downhill.",
            isCritical = true
          ),
          InstructionItem(
            "Listen for rumbling",
            "A low roar, cracking trees or rocks bouncing downhill means move NOW.",
            isCritical = true
          ),
          InstructionItem(
            "Do not return for belongings",
            "A moving slope can bury a house in seconds."
          ),
          InstructionItem(
            "Warn neighbors downhill",
            "Shout, call or use the siren so people below the slope get out in time."
          ),
          InstructionItem(
            "Never cross an active slide area",
            "Mud, rocks and debris keep moving. Find another route on higher, stable ground."
          ),
          InstructionItem(
            "Avoid debris-covered roads and bridges",
            "A cracked or buried road can collapse under weight. Wait for official clearance."
          )
        )
      ),
      after = InstructionPhase(
        title = "After",
        items = listOf(
          InstructionItem(
            "Stay away from the slide area",
            "Further slides are common after the first failure. Let officials inspect first.",
            isCritical = true
          ),
          InstructionItem(
            "Check for trapped neighbors — from a safe distance",
            "Report locations to rescue teams instead of digging alone."
          ),
          InstructionItem(
            "Report to the district control room",
            "Report slope cracks, blocked roads and damaged houses to the village officer."
          ),
          InstructionItem(
            "Expect damaged water and power",
            "Springs and pipes may be contaminated or severed; treat all drinking water."
          ),
          InstructionItem(
            "Follow authority instructions",
            "Return to slopes and buildings only after officials inspect and clear the area."
          ),
          InstructionItem(
            "Report blocked roads and cracks",
            "Use the incident report on the Radar tab and inform the village officer about new slope cracks."
          )
        )
      )
    ),
    DisasterCategory(
      id = "fire",
      title = "Fire",
      subtitle = "Household, grassland & plantation guidance",
      before = InstructionPhase(
        title = "Before",
        items = listOf(
          InstructionItem(
            "Install and test smoke alarms",
            "Test monthly; replace batteries yearly. A working alarm cuts fire deaths drastically.",
            isCritical = true
          ),
          InstructionItem(
            "Plan two escape routes",
            "Every room should have two exits. Practice the drill with children and elderly family members."
          ),
          InstructionItem(
            "Store fuel and cylinders correctly",
            "Keep LPG cylinders upright, away from heat; turn off the regulator at night."
          ),
          InstructionItem(
            "Clear dry vegetation around homes",
            "In dry season, grassland fire spreads fast - keep a fire break around the house."
          ),
          InstructionItem(
            "Keep escape routes clear",
            "Do not store boxes, vehicles or scrap in staircases, corridors or doorways."
          ),
          InstructionItem(
            "Know where safety equipment is",
            "Extinguishers and sand buckets help only on small, starting fires - know their location in advance."
          ),
          InstructionItem(
            "Keep emergency numbers handy",
            "Save 101 (Fire) and 112 (Emergency) where every family member can find them fast."
          )
        )
      ),
      during = InstructionPhase(
        title = "During",
        items = listOf(
          InstructionItem(
            "Get out and stay out",
            "Leave belongings. Crawl low under smoke. Never use a lift during a fire.",
            isCritical = true
          ),
          InstructionItem(
            "Stop-drop-roll if clothes catch fire",
            "Running fans the flames."
          ),
          InstructionItem(
            "Close doors behind you",
            "A closed door slows the spread by minutes — but never lock anyone in."
          ),
          InstructionItem(
            "Call 101 (Fire) and 112 (Emergency)",
            "Report location landmarks - plantation names, road numbers - for faster response."
          ),
          InstructionItem(
            "Raise the alarm first",
            "Shout Fire, press the alarm, and alert neighbours as you leave - if it is safe to do so."
          ),
          InstructionItem(
            "Test doors before opening",
            "If a door is hot or smoke seeps through it, use your second escape route."
          ),
          InstructionItem(
            "Use an extinguisher only with a safe exit behind you",
            "Fight a fire only if it is small and only with an escape route at your back - otherwise get out and call for help."
          )
        )
      ),
      after = InstructionPhase(
        title = "After",
        items = listOf(
          InstructionItem(
            "Do not re-enter until cleared",
            "Hot spots and structural damage kill after the flames are out.",
            isCritical = true
          ),
          InstructionItem(
            "Have the wiring checked",
            "Get a licensed electrician to inspect before restoring power."
          ),
          InstructionItem(
            "Document damage for relief",
            "Photograph and list losses for insurance and relief claims."
          ),
          InstructionItem(
            "Watch for hidden dangers",
            "Smouldering spots, hot surfaces, damaged wiring and weak floors can hurt long after the flames are out."
          ),
          InstructionItem(
            "Get medical help when needed",
            "Smoke inhalation and burns can worsen hours later â go to a hospital even if injuries look minor."
          ),
          InstructionItem(
            "Follow official instructions",
            "Wait for fire and rescue teams to declare the building safe before any return."
          )
        )
      )
    ),
    DisasterCategory(
      id = "earthquake",
      title = "Earthquake",
      subtitle = "Drop, cover and hold guidance",
      before = InstructionPhase(
        title = "Before",
        items = listOf(
          InstructionItem(
            "Secure heavy furniture",
            "Anchor cupboards, shelves and water tanks to walls — falling objects are the main injury cause."
          ),
          InstructionItem(
            "Identify safe spots",
            "Under sturdy tables, inside door frames, against interior walls — away from windows and shelves."
          ),
          InstructionItem(
            "Keep the go-bag by the door",
            "The same kit serves flood and earthquake evacuation."
          ),
          InstructionItem(
            "Practice the drill with your family",
            "Rehearse drop-cover-hold and your exit route so the actions come automatically."
          )
        )
      ),
      during = InstructionPhase(
        title = "During",
        items = listOf(
          InstructionItem(
            "DROP — COVER — HOLD ON",
            "Drop to the floor, cover your head under sturdy furniture, hold until shaking stops.",
            isCritical = true
          ),
          InstructionItem(
            "Stay away from windows",
            "Glass shatters outward and can cause severe cuts.",
            isCritical = true
          ),
          InstructionItem(
            "If outside, move to open ground",
            "Keep away from buildings, trees, power lines and walls."
          ),
          InstructionItem(
            "Do not use lifts",
            "Use stairs only after shaking stops."
          ),
          InstructionItem(
            "If driving, stop safely and stay inside",
            "Pull over away from bridges, wires and slopes; remain in the vehicle until shaking stops."
          )
        )
      ),
      after = InstructionPhase(
        title = "After",
        items = listOf(
          InstructionItem(
            "Expect aftershocks",
            "Aftershocks can collapse weakened structures — stay out of damaged buildings.",
            isCritical = true
          ),
          InstructionItem(
            "Check for injuries and gas leaks",
            "Shut off gas if you smell it; use a torch, never a flame."
          ),
          InstructionItem(
            "Use stairs, not lifts",
            "Structural and electrical damage makes elevators unsafe."
          ),
          InstructionItem(
            "Wait for clearance to re-enter",
            "Strong aftershocks and gas or utility damage can collapse buildings — only return once authorities inspect and declare the area safe."
          )
        )
      )
    )
  )

  // --------------------------------------------------------------- common
  val commonModules: List<CommonModule> = listOf(
    CommonModule(
      id = "emergency_contacts",
      title = "Emergency Contacts",
      subtitle = "Official Indian emergency lines",
      items = listOf(
        InstructionItem("112 — National Emergency Number", "Police, Fire, Health — one number for all emergencies.", isCritical = true),
        InstructionItem("101 — Fire & Rescue", "Report fires, rescues and gas leaks."),
        InstructionItem("100 — Police", "Law enforcement and rescue coordination."),
        // Region-scoped lines are labelled so users outside that state can tell
        // them apart from the national numbers above.
        InstructionItem(
          "108 — Ambulance (Kerala)",
          "Free emergency ambulance service in Kerala.",
          region = "Kerala"
        ),
        InstructionItem(
          "1077 — District Relief Control Room",
          "Flood, landslide and relief coordination at the district level."
        ),
        InstructionItem(
          "KSDMA — Kerala State Disaster Management Authority",
          "State-level disaster warnings and advisories.",
          region = "Kerala"
        )
      )
    ),
    CommonModule(
      id = "evacuation",
      title = "Evacuation Essentials",
      subtitle = "What to do when an order arrives",
      items = listOf(
        InstructionItem("Leave early when ordered", "Late evacuation gets caught in the hazard itself.", isCritical = true),
        InstructionItem("Take the go-bag and documents", "ID, medicines, some cash, phone charger — nothing heavier."),
        InstructionItem("Cut power, gas and water", "Shut mains before locking the house to prevent secondary accidents."),
        InstructionItem("Follow official routes only", "The app's evacuation route avoids marked hazard zones; do not take shortcuts through water or slope areas."),
        InstructionItem("Register at the shelter", "Give names and household count so capacity tracking and family tracing stay accurate.")
      )
    ),
    CommonModule(
      id = "emergency_kit",
      title = "Emergency Kit",
      subtitle = "72-hour self-reliance pack",
      items = listOf(
        InstructionItem("Water — 2L per person per day", "Three days minimum; purification tablets as backup."),
        InstructionItem("Non-perishable food", "Dry rations, high-protein bars, ORS sachets for three days."),
        InstructionItem("First-aid kit", "Bandages, antiseptic, fever and diarrhea medicine, personal prescriptions."),
        InstructionItem("Torch + power bank", "Prefer hand-crank or battery torch; candles cause fires."),
        InstructionItem("Whistle", "Signals rescuers when voice fails — three blasts means help."),
        InstructionItem("Documents pouch", "ID copies, land/ration cards, medical prescriptions — sealed and waterproof."),
        InstructionItem("Radio or phone with FM", "Official bulletins reach you when data networks fail."),
        InstructionItem("Cash in small notes", "ATMs and card networks may be down after a disaster.")
      )
    )
  )
}



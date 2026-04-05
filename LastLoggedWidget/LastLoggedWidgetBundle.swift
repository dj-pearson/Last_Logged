import WidgetKit
import SwiftUI

@main
struct LastLoggedWidgetBundle: WidgetBundle {
    var body: some Widget {
        LastLoggedWidget()
        if #available(iOS 16.1, *) {
            LastLoggedLiveActivity()
        }
    }
}

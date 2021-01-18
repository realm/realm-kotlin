import SwiftUI
import shared

struct ContentView: View {
    let calculator = Calculator.Companion()
    let greet = Greeting().greeting()
    
    @State private var firstNum: String = "0"
    @State private var secondNum: String = "0"
    
    @State var token: LibraryCancellable? = nil
        
    private var sum: String {
        if let firstNum = Int32(firstNum), let secondNum = Int32(secondNum) {
            return String(calculator.sum(a: firstNum, b: secondNum))
        } else {
            return "🤔"
        }
    }
    
    private var count: Int {
        return self.calculator.history().count
    }
    
    var body: some View {
        VStack(alignment: .center) {
            Text(greet)
            HStack(alignment: .center) {
                TextField("A", text: $firstNum)
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.center)
                    .frame(width: 30)
                Text("+")
                TextField("B", text: $secondNum)
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.center)
                    .frame(width: 30)
                Text("=")
                Text(sum)
            }
            Text("History count: \(count)")
        }.onAppear() {
            self.token = self.listen()
        }.onDisappear() {
            self.token?.cancel()
        }
    }
    
    private func listen() -> LibraryCancellable {
        do {
            return try calculator.listen {
                print("History updated \(self.calculator.history().count)")
            }
        } catch {
            fatalError("Failed to register for notifications: \(error)")
        }
    }
    
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}

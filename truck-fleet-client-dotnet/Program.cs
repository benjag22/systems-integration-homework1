using Grpc.Core;
using Grpc.Net.Client;
using Trucks;

const string defaultAddress = "http://localhost:9090";

if (args.Length == 0)
{
    PrintUsage();
    return 2;
}

var command = args[0].ToLowerInvariant();
var address = Environment.GetEnvironmentVariable("FLEET_GRPC_ADDRESS") ?? defaultAddress;

if (command == "list" && args.Length > 1)
{
    address = args[1];
}
else if (command is "get" or "availability" && args.Length > 2)
{
    address = args[2];
}

if (command is "get" or "availability" &&
    (args.Length < 2 || !int.TryParse(args[1], out _)))
{
    PrintUsage();
    return 2;
}

try
{
    using var channel = GrpcChannel.ForAddress(address);
    var client = new TruckService.TruckServiceClient(channel);
    var deadline = DateTime.UtcNow.AddSeconds(10);

    switch (command)
    {
        case "availability":
        {
            var response = await client.CheckAvailabilityAsync(
                new GetTruckRequest { TruckId = int.Parse(args[1]) },
                deadline: deadline);
            Console.WriteLine($"Camión: {response.TruckId}");
            Console.WriteLine($"Capacidad disponible: {response.AvailableCapacityKg} kg");
            Console.WriteLine($"Capacidad máxima: {response.MaxCapacityKg} kg");
            break;
        }
        case "get":
        {
            var response = await client.GetTruckAsync(
                new GetTruckRequest { TruckId = int.Parse(args[1]) },
                deadline: deadline);
            Console.WriteLine($"Camión {response.Truck.Id}: {response.Truck.LicensePlate}");
            Console.WriteLine($"Capacidad máxima: {response.Truck.MaxCapacityKg} kg");
            Console.WriteLine($"Cargas registradas: {response.Truck.Loads.Count}");
            Console.WriteLine($"Rutas asignadas: {response.Routes.Count}");
            break;
        }
        case "list":
        {
            var response = await client.ListTrucksAsync(new ListTrucksRequest(), deadline: deadline);
            foreach (var item in response.Trucks)
            {
                Console.WriteLine($"{item.Truck.Id}: {item.Truck.LicensePlate} " +
                                  $"({item.Truck.MaxCapacityKg} kg, {item.Truck.Loads.Count} cargas)");
            }
            Console.WriteLine($"Total de camiones: {response.Trucks.Count}");
            break;
        }
        default:
            PrintUsage();
            return 2;
    }

    return 0;
}
catch (RpcException exception)
{
    Console.Error.WriteLine($"Error gRPC ({exception.StatusCode}): {exception.Status.Detail}");
    return 1;
}
catch (Exception exception) when (exception is HttpRequestException or InvalidOperationException)
{
    Console.Error.WriteLine($"No se pudo conectar con Flota en {address}: {exception.Message}");
    return 1;
}

static void PrintUsage()
{
    Console.WriteLine("Cliente gRPC C# de Flota");
    Console.WriteLine("Uso:");
    Console.WriteLine("  dotnet run -- availability <truckId> [address]");
    Console.WriteLine("  dotnet run -- get <truckId> [address]");
    Console.WriteLine("  dotnet run -- list [address]");
    Console.WriteLine("También puedes configurar FLEET_GRPC_ADDRESS (por defecto: http://localhost:9090).");
}